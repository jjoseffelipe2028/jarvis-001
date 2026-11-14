package io.github.jjoseffelipe2028.bracos.central;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
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

import io.github.jjoseffelipe2028.bracos.acoes.estudio.EstudioService;
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

/** Ticket 047: o Estúdio pela Central, atrás do crachá. O Estúdio aqui é FALSO. 🎮 */
@WebMvcTest({CentralEstudioController.class, CentralController.class})
@Import({ChaveDaCentral.class, SessoesDaCentral.class, QuadroDeEventos.class, WebConfig.class, ErrosHandler.class})
@ExtendWith(OutputCaptureExtension.class)
class CentralEstudioControllerTest {

    private static final Path ARQUIVO;
    private static final String ORIGEM = "http://localhost:80";

    static {
        try {
            ARQUIVO = Files.createTempDirectory("central-estudio-teste").resolve(".central-chave");
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

    @MockitoBean
    private EstudioService estudio;

    @MockitoBean
    private PendenciasService pendencias; // só para o CentralController subir

    @MockitoBean
    private GitStatusService git; // só para o CentralController subir

    private Cookie entrar() throws Exception {
        return mockMvc.perform(post("/central/sessao").header(CentralInterceptor.CABECALHO, "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"chave\":\"" + Files.readString(ARQUIVO) + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getCookie(CentralInterceptor.COOKIE);
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder pedido(Cookie cracha, String corpo) {
        var p = post("/central/acoes/estudio").header(CentralInterceptor.CABECALHO, "1").header("Origin", ORIGEM)
                .contentType(MediaType.APPLICATION_JSON).content(corpo);
        return cracha == null ? p : p.cookie(cracha);
    }

    @Test
    void comCracha_comecaOPedido_eOLogNaoGuardaOTexto(CapturedOutput saida) throws Exception {
        when(estudio.iniciar(eq("jogo da nave secreta"), eq(true)))
                .thenReturn(new EstudioService.Estado("20261113-x", "planejando", List.of("📥 Pedido recebido."), "", false, false));
        mockMvc.perform(pedido(entrar(), "{\"texto\":\"jogo da nave secreta\",\"revisao\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.etapa").value("planejando"))
                .andExpect(jsonPath("$.id").value("20261113-x"));
        assertTrue(saida.getOut().contains("cliente=central acao=estudio resultado=iniciado revisao=true letras=20"));
        assertFalse(saida.getOut().contains("nave secreta"), "o pedido não vai para o log");
    }

    @Test
    void semCracha_401_semOrigin_403_siteDeFora_403_eNadaComeca() throws Exception {
        String corpo = "{\"texto\":\"jogo\",\"revisao\":true}";
        mockMvc.perform(pedido(null, corpo)).andExpect(status().isUnauthorized());
        Cookie cracha = entrar();
        mockMvc.perform(post("/central/acoes/estudio").cookie(cracha).header(CentralInterceptor.CABECALHO, "1")
                .contentType(MediaType.APPLICATION_JSON).content(corpo)).andExpect(status().isForbidden());
        mockMvc.perform(post("/central/acoes/estudio").cookie(cracha).header(CentralInterceptor.CABECALHO, "1")
                .header("Origin", "https://site-malicioso.example").contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/central/acoes/estudio").header(CentralInterceptor.CABECALHO, "1")).andExpect(status().isUnauthorized());
        verify(estudio, never()).iniciar(any(), anyBoolean());
    }

    @Test
    void pedidoVazioOuGrande_400_ocupado_503() throws Exception {
        Cookie cracha = entrar();
        mockMvc.perform(pedido(cracha, "{\"texto\":\"  \"}")).andExpect(status().isBadRequest());
        mockMvc.perform(pedido(cracha, "{\"texto\":\"" + "a".repeat(2001) + "\"}")).andExpect(status().isBadRequest());
        verify(estudio, never()).iniciar(any(), anyBoolean());
        when(estudio.iniciar(any(), anyBoolean())).thenThrow(new AcaoIndisponivelException("estudio ocupado"));
        mockMvc.perform(pedido(cracha, "{\"texto\":\"jogo\"}")).andExpect(status().isServiceUnavailable());
    }

    @Test
    void estado_comCracha() throws Exception {
        when(estudio.estado()).thenReturn(new EstudioService.Estado("x", "pronto", List.of("✅"), "Fiz.", true, true));
        mockMvc.perform(get("/central/acoes/estudio").cookie(entrar()).header(CentralInterceptor.CABECALHO, "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resumo").value("Fiz."))
                .andExpect(jsonPath("$.terminou").value(true));
    }
}
