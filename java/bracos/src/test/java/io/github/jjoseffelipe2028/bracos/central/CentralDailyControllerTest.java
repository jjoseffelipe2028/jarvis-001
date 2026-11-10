package io.github.jjoseffelipe2028.bracos.central;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import io.github.jjoseffelipe2028.bracos.acoes.daily.DailyService;
import io.github.jjoseffelipe2028.bracos.acoes.git.GitStatusService;
import io.github.jjoseffelipe2028.bracos.acoes.pendencias.PendenciasService;
import io.github.jjoseffelipe2028.bracos.erros.AcaoIndisponivelException;
import io.github.jjoseffelipe2028.bracos.erros.ErrosHandler;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Ticket 020: daily pela Central, a 1ª ação que ESCREVE. Caminho feliz e ataques. 🔒 */
@WebMvcTest({CentralDailyController.class, CentralController.class}) // CentralController: crachá e eventos
@Import({ChaveDaCentral.class, SessoesDaCentral.class, QuadroDeEventos.class, WebConfig.class, ErrosHandler.class})
@ExtendWith(OutputCaptureExtension.class)
class CentralDailyControllerTest {

    private static final String ORIGEM = "http://localhost:80"; // a própria Central no MockMvc
    private static final Path ARQUIVO;

    static {
        try {
            ARQUIVO = Files.createTempDirectory("central-daily-teste").resolve(".central-chave");
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
    private DailyService daily;

    @MockitoBean
    private PendenciasService pendencias; // só para o CentralController subir

    @MockitoBean
    private GitStatusService git; // só para o CentralController subir

    private Cookie entrar() throws Exception {
        MvcResult r = mockMvc.perform(post("/central/sessao")
                        .header(CentralInterceptor.CABECALHO, "1").header("Origin", ORIGEM)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"chave\":\"" + Files.readString(ARQUIVO) + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return r.getResponse().getCookie(CentralInterceptor.COOKIE);
    }

    /** Pedido certinho, como a Central manda. Cada teste tira ou estraga uma parte. */
    private static MockHttpServletRequestBuilder salvar(Cookie cracha, String texto) {
        MockHttpServletRequestBuilder p = post("/central/acoes/daily")
                .header(CentralInterceptor.CABECALHO, "1").header("Origin", ORIGEM)
                .contentType(MediaType.APPLICATION_JSON).content("{\"texto\":\"" + texto + "\"}");
        return cracha == null ? p : p.cookie(cracha);
    }

    private QuadroDeEventos.Evento ultimoEvento() {
        List<QuadroDeEventos.Evento> eventos = quadro.depois(0).eventos();
        assertFalse(eventos.isEmpty(), "o pedido devia virar evento no quadro");
        return eventos.get(eventos.size() - 1);
    }

    @Test
    void caminhoFeliz_salva_naoLogaOTexto_eViraEvento(CapturedOutput saida) throws Exception {
        when(daily.registrar("estudei a aula 02")).thenReturn("2026-11-10.md");

        mockMvc.perform(salvar(entrar(), "estudei a aula 02"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultado").value("ok"))
                .andExpect(jsonPath("$.arquivo").value("2026-11-10.md"));

        verify(daily).registrar("estudei a aula 02");
        assertTrue(saida.getOut().contains("cliente=central acao=daily resultado=ok"));
        assertFalse(saida.getOut().contains("estudei a aula 02"), "texto do usuário não vai para o log");
        QuadroDeEventos.Evento e = ultimoEvento();
        assertEquals("daily", e.acao());
        assertEquals("ok", e.resultado());
        assertEquals("central", e.cliente());
    }

    @Test
    void semCracha_401_eONaoEscreve_eEscritorioVeORecusado() throws Exception {
        mockMvc.perform(salvar(null, "oi")).andExpect(status().isUnauthorized());
        verify(daily, never()).registrar(anyString());
        assertEquals("recusado", ultimoEvento().resultado());
    }

    @Test
    void semOrigin_403_soNaEscrita() throws Exception {
        Cookie cracha = entrar();
        mockMvc.perform(post("/central/acoes/daily").cookie(cracha)
                        .header(CentralInterceptor.CABECALHO, "1")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"texto\":\"oi\"}"))
                .andExpect(status().isForbidden());
        verify(daily, never()).registrar(anyString());
    }

    @Test
    void leituraSemOrigin_continuaLiberada() throws Exception {
        // A regra nova vale só para escrita: o quadro de eventos (GET) segue funcionando sem Origin
        mockMvc.perform(get("/central/acoes/eventos").cookie(entrar())
                        .header(CentralInterceptor.CABECALHO, "1"))
                .andExpect(status().isOk());
    }

    @Test
    void siteDeFora_403() throws Exception {
        Cookie cracha = entrar();
        mockMvc.perform(post("/central/acoes/daily").cookie(cracha)
                        .header(CentralInterceptor.CABECALHO, "1").header("Origin", "https://site-malicioso.example")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"texto\":\"oi\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(salvar(cracha, "oi").header("Sec-Fetch-Site", "cross-site"))
                .andExpect(status().isForbidden());
        verify(daily, never()).registrar(anyString());
    }

    @Test
    void semCabecalhoDaCentral_401() throws Exception {
        mockMvc.perform(post("/central/acoes/daily").cookie(entrar()).header("Origin", ORIGEM)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"texto\":\"oi\"}"))
                .andExpect(status().isUnauthorized());
        verify(daily, never()).registrar(anyString());
    }

    @Test
    void textoVazioOuGrandeDemais_400() throws Exception {
        Cookie cracha = entrar();
        mockMvc.perform(salvar(cracha, "   ")).andExpect(status().isBadRequest());
        mockMvc.perform(salvar(cracha, "a".repeat(2001))).andExpect(status().isBadRequest());
        verify(daily, never()).registrar(anyString());
        assertEquals("invalido", ultimoEvento().resultado());
    }

    @Test
    void campoExtraDePasta_eIgnorado() throws Exception {
        // Não existe campo de arquivo/pasta: quem escolhe onde gravar é o servidor
        when(daily.registrar("oi")).thenReturn("2026-11-10.md");
        mockMvc.perform(post("/central/acoes/daily").cookie(entrar())
                        .header(CentralInterceptor.CABECALHO, "1").header("Origin", ORIGEM)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"texto\":\"oi\",\"arquivo\":\"../../Windows/win.ini\"}"))
                .andExpect(status().isOk());
        verify(daily).registrar("oi");
    }

    @Test
    void pastaNaoConfigurada_503() throws Exception {
        when(daily.registrar("oi")).thenThrow(new AcaoIndisponivelException("pasta do daily nao configurada"));
        mockMvc.perform(salvar(entrar(), "oi")).andExpect(status().isServiceUnavailable());
        assertEquals("indisponivel", ultimoEvento().resultado());
    }
}
