package io.github.jjoseffelipe2028.bracos.central;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
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

import io.github.jjoseffelipe2028.bracos.acoes.estudio.CortesService;
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

/** Ticket 048: o Estúdio de cortes pela Central, atrás do crachá. O serviço aqui é FALSO. 🎬 */
@WebMvcTest({CentralCortesController.class, CentralController.class})
@Import({ChaveDaCentral.class, SessoesDaCentral.class, QuadroDeEventos.class, WebConfig.class, ErrosHandler.class})
@ExtendWith(OutputCaptureExtension.class)
class CentralCortesControllerTest {

    private static final Path ARQUIVO;
    private static final String ORIGEM = "http://localhost:80";

    static {
        try {
            ARQUIVO = Files.createTempDirectory("central-cortes-teste").resolve(".central-chave");
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
    private CortesService cortes;

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
        var p = post("/central/acoes/cortes").header(CentralInterceptor.CABECALHO, "1").header("Origin", ORIGEM)
                .contentType(MediaType.APPLICATION_JSON).content(corpo);
        return cracha == null ? p : p.cookie(cracha);
    }

    @Test
    void comCracha_listaEComeca_eOLogNaoGuardaNomeNemLink(CapturedOutput saida) throws Exception {
        Cookie cracha = entrar();
        when(cortes.videos()).thenReturn(List.of(new CortesService.Video("live-secreta.mp4", 12)));
        mockMvc.perform(get("/central/acoes/cortes/videos").cookie(cracha).header(CentralInterceptor.CABECALHO, "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.videos[0].nome").value("live-secreta.mp4"));
        when(cortes.iniciar(eq(null), eq("https://youtu.be/dQw4w9WgXcQ"), eq(true), eq("@amigo"), eq(5), eq("en"), eq(true)))
                .thenReturn(new CortesService.Estado("comecando", List.of("📥"), List.of(), "", false, false));
        mockMvc.perform(pedido(cracha, "{\"link\":\"https://youtu.be/dQw4w9WgXcQ\",\"autorizado\":true,\"arroba\":\"@amigo\","
                        + "\"top\":5,\"idioma\":\"en\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.etapa").value("comecando"));
        assertTrue(saida.getOut().contains("cliente=central acao=cortes resultado=iniciado link=true dupla=true"));
        assertFalse(saida.getOut().contains("dQw4w9WgXcQ") || saida.getOut().contains("live-secreta"));
    }

    @Test
    void semCracha_401_semOrigin_403_eNadaComeca() throws Exception {
        String corpo = "{\"video\":\"live.mp4\",\"arroba\":\"amigo\"}";
        mockMvc.perform(pedido(null, corpo)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/central/acoes/cortes/videos").header(CentralInterceptor.CABECALHO, "1")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/central/acoes/cortes").cookie(entrar()).header(CentralInterceptor.CABECALHO, "1")
                .contentType(MediaType.APPLICATION_JSON).content(corpo)).andExpect(status().isForbidden());
        verify(cortes, never()).iniciar(any(), any(), anyBoolean(), any(), anyInt(), any(), anyBoolean());
    }

    @Test
    void foraDoLimite_400_ocupado_503() throws Exception {
        Cookie cracha = entrar();
        for (String corpo : List.of("{\"video\":\"live.mp4\"}", "{\"video\":\"live.mp4\",\"arroba\":\"a\",\"top\":21}",
                "{\"video\":\"live.mp4\",\"arroba\":\"a\",\"top\":0}", "{\"video\":\"live.mp4\",\"arroba\":\"a\",\"idioma\":\"fra\"}")) {
            mockMvc.perform(pedido(cracha, corpo)).andExpect(status().isBadRequest());
        }
        verify(cortes, never()).iniciar(any(), any(), anyBoolean(), any(), anyInt(), any(), anyBoolean());
        when(cortes.iniciar(any(), any(), anyBoolean(), any(), anyInt(), any(), anyBoolean()))
                .thenThrow(new AcaoIndisponivelException("cortes ocupado"));
        mockMvc.perform(pedido(cracha, "{\"video\":\"live.mp4\",\"arroba\":\"a\"}")).andExpect(status().isServiceUnavailable());
    }
}
