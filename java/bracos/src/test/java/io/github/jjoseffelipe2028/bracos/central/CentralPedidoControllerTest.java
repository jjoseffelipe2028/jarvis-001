package io.github.jjoseffelipe2028.bracos.central;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import io.github.jjoseffelipe2028.bracos.acoes.git.GitStatusService;
import io.github.jjoseffelipe2028.bracos.acoes.pedidos.PedidoService;
import io.github.jjoseffelipe2028.bracos.acoes.pendencias.PendenciasService;
import io.github.jjoseffelipe2028.bracos.erros.AcaoIndisponivelException;
import io.github.jjoseffelipe2028.bracos.erros.ErrosHandler;
import io.github.jjoseffelipe2028.bracos.erros.LimiteAtingidoException;
import io.github.jjoseffelipe2028.bracos.seguranca.WebConfig;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Ticket 060: o pedido revisado vira nota na caixa da 001. Caminho feliz e ataques. 📬🔒 */
@WebMvcTest({CentralPedidoController.class, CentralController.class}) // CentralController: crachá e eventos
@Import({ChaveDaCentral.class, SessoesDaCentral.class, QuadroDeEventos.class, WebConfig.class, ErrosHandler.class})
@ExtendWith(OutputCaptureExtension.class)
class CentralPedidoControllerTest {

    private static final String ORIGEM = "http://localhost:80"; // a própria Central no MockMvc
    private static final String ROTA = "/central/acoes/pedido-001";
    private static final Path ARQUIVO;

    static {
        try {
            ARQUIVO = Files.createTempDirectory("central-pedido-teste").resolve(".central-chave");
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void propriedades(DynamicPropertyRegistry registro) {
        registro.add("jarvis.token", () -> "token-de-teste"); // valor FICTÍCIO
        registro.add("jarvis.central.arquivo-chave", ARQUIVO::toString);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private QuadroDeEventos quadro;

    @MockitoBean
    private PedidoService pedidos;

    @MockitoBean
    private PendenciasService pendencias; // só para o CentralController subir

    @MockitoBean
    private GitStatusService git; // só para o CentralController subir

    private Cookie entrar() throws Exception {
        return mockMvc.perform(post("/central/sessao")
                        .header(CentralInterceptor.CABECALHO, "1").header("Origin", ORIGEM)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"chave\":\"" + Files.readString(ARQUIVO) + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getCookie(CentralInterceptor.COOKIE);
    }

    private static MockHttpServletRequestBuilder anotar(Cookie cracha, String corpo) {
        MockHttpServletRequestBuilder p = post(ROTA)
                .header(CentralInterceptor.CABECALHO, "1").header("Origin", ORIGEM)
                .contentType(MediaType.APPLICATION_JSON).content(corpo);
        return cracha == null ? p : p.cookie(cracha);
    }

    private String ultimoResultado() {
        List<QuadroDeEventos.Evento> eventos = quadro.depois(0).eventos();
        QuadroDeEventos.Evento e = eventos.get(eventos.size() - 1);
        assertEquals("pedido001", e.acao());
        return e.resultado();
    }

    @Test
    void caminhoFeliz_anota_naoLogaOTexto_eViraEvento(CapturedOutput saida) throws Exception {
        when(pedidos.anotar("cria um jogo de nave")).thenReturn("2026-11-15-143205.md");
        mockMvc.perform(anotar(entrar(), "{\"texto\":\"cria um jogo de nave\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultado").value("ok"))
                .andExpect(jsonPath("$.arquivo").value("2026-11-15-143205.md"));
        assertTrue(saida.getOut().contains("cliente=central acao=pedido001 resultado=ok"));
        assertFalse(saida.getOut().contains("jogo de nave"), "o pedido não vai para o log");
        assertEquals("ok", ultimoResultado());
    }

    @Test
    void semCracha_401_semOrigin_403_siteDeFora_403() throws Exception {
        mockMvc.perform(anotar(null, "{\"texto\":\"oi\"}")).andExpect(status().isUnauthorized());
        Cookie cracha = entrar();
        mockMvc.perform(post(ROTA).cookie(cracha).header(CentralInterceptor.CABECALHO, "1")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"texto\":\"oi\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(ROTA).cookie(cracha).header(CentralInterceptor.CABECALHO, "1")
                        .header("Origin", "https://site-malicioso.example")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"texto\":\"oi\"}"))
                .andExpect(status().isForbidden());
        verify(pedidos, never()).anotar(anyString());
    }

    @Test
    void textoVazioOuGrandeDemais_400() throws Exception {
        Cookie cracha = entrar();
        mockMvc.perform(anotar(cracha, "{\"texto\":\"   \"}")).andExpect(status().isBadRequest());
        mockMvc.perform(anotar(cracha, "{\"texto\":\"" + "a".repeat(2001) + "\"}")).andExpect(status().isBadRequest());
        verify(pedidos, never()).anotar(anyString());
        assertEquals("invalido", ultimoResultado());
    }

    @Test
    void campoExtraDeArquivo_eIgnorado() throws Exception {
        when(pedidos.anotar("oi")).thenReturn("2026-11-15-143205.md");
        mockMvc.perform(anotar(entrar(), "{\"texto\":\"oi\",\"arquivo\":\"../../Windows/win.ini\"}"))
                .andExpect(status().isOk());
        verify(pedidos).anotar("oi");
    }

    @Test
    void pastaNaoConfigurada_503_limiteDoDia_429() throws Exception {
        Cookie cracha = entrar();
        when(pedidos.anotar("oi")).thenThrow(new AcaoIndisponivelException("pasta nao configurada"));
        mockMvc.perform(anotar(cracha, "{\"texto\":\"oi\"}")).andExpect(status().isServiceUnavailable());
        assertEquals("indisponivel", ultimoResultado());
        when(pedidos.anotar("de novo")).thenThrow(new LimiteAtingidoException("limite de pedidos do dia"));
        mockMvc.perform(anotar(cracha, "{\"texto\":\"de novo\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.erro").value("limite de pedidos do dia"));
    }
}
