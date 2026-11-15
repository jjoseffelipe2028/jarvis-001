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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import io.github.jjoseffelipe2028.bracos.acoes.estudio.BraboService;
import io.github.jjoseffelipe2028.bracos.acoes.git.GitStatusService;
import io.github.jjoseffelipe2028.bracos.acoes.pendencias.PendenciasService;
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

/** Ticket 052: vídeo do apresentador pela Central, atrás do crachá. O serviço aqui é FALSO. 🎙️ */
@WebMvcTest({CentralBraboController.class, CentralController.class})
@Import({ChaveDaCentral.class, SessoesDaCentral.class, QuadroDeEventos.class, WebConfig.class, ErrosHandler.class})
@ExtendWith(OutputCaptureExtension.class)
class CentralBraboControllerTest {

    private static final Path PASTA;
    private static final String ORIGEM = "http://localhost:80";

    static {
        try {
            PASTA = Files.createTempDirectory("central-brabo-teste");
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void propriedades(DynamicPropertyRegistry registro) {
        registro.add("jarvis.token", () -> "token-de-teste"); // valor FICTÍCIO
        registro.add("jarvis.central.arquivo-chave", () -> PASTA.resolve(".central-chave").toString());
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BraboService brabo;

    @MockitoBean
    private PendenciasService pendencias; // só para o CentralController subir

    @MockitoBean
    private GitStatusService git; // só para o CentralController subir

    private Cookie entrar() throws Exception {
        return mockMvc.perform(post("/central/sessao").header(CentralInterceptor.CABECALHO, "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"chave\":\"" + Files.readString(PASTA.resolve(".central-chave")) + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getCookie(CentralInterceptor.COOKIE);
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder pedido(Cookie cracha, String corpo) {
        var p = post("/central/acoes/brabo").header(CentralInterceptor.CABECALHO, "1").header("Origin", ORIGEM)
                .contentType(MediaType.APPLICATION_JSON).content(corpo);
        return cracha == null ? p : p.cookie(cracha);
    }

    @Test
    void comCracha_comeca_eOLogNaoGuardaOTema(CapturedOutput saida) throws Exception {
        Cookie cracha = entrar();
        when(brabo.iniciar(eq("mestre"), eq("tema-secreto do chuveiro"), eq(null), eq(null), eq(true)))
                .thenReturn(new BraboService.Estado("comecando", List.of("📝"), List.of(), "", "", false, false));
        mockMvc.perform(pedido(cracha, "{\"personagem\":\"mestre\",\"tema\":\"tema-secreto do chuveiro\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.etapa").value("comecando"));
        assertTrue(saida.getOut().contains("cliente=central acao=brabo resultado=iniciado dupla=true"));
        assertFalse(saida.getOut().contains("tema-secreto"));
    }

    @Test
    void video_soComCracha_eSoNomeConhecido() throws Exception {
        Path mp4 = Files.writeString(PASTA.resolve("x-mestre.mp4"), "mp4");
        when(brabo.video("x-mestre.mp4")).thenReturn(Optional.of(mp4));
        when(brabo.video("outro-mestre.mp4")).thenReturn(Optional.empty());
        mockMvc.perform(get("/central/acoes/brabo/video/x-mestre.mp4").header(CentralInterceptor.CABECALHO, "1"))
                .andExpect(status().isUnauthorized());
        Cookie cracha = entrar();
        mockMvc.perform(get("/central/acoes/brabo/video/x-mestre.mp4").cookie(cracha).header(CentralInterceptor.CABECALHO, "1"))
                .andExpect(status().isOk()).andExpect(header().string("Content-Type", "video/mp4"))
                .andExpect(content().string("mp4"));
        mockMvc.perform(get("/central/acoes/brabo/video/outro-mestre.mp4").cookie(cracha).header(CentralInterceptor.CABECALHO, "1"))
                .andExpect(status().isNotFound());
    }

    @Test
    void semCracha_401_semOrigin_403_temaGrande_400_eNadaComeca() throws Exception {
        String corpo = "{\"personagem\":\"mestre\",\"tema\":\"chuveiro\"}";
        mockMvc.perform(pedido(null, corpo)).andExpect(status().isUnauthorized());
        Cookie cracha = entrar();
        mockMvc.perform(post("/central/acoes/brabo").cookie(cracha).header(CentralInterceptor.CABECALHO, "1")
                .contentType(MediaType.APPLICATION_JSON).content(corpo)).andExpect(status().isForbidden());
        mockMvc.perform(pedido(cracha, "{\"personagem\":\"mestre\",\"tema\":\"" + "a".repeat(301) + "\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(pedido(cracha, "{\"personagem\":\"mestre\"}")).andExpect(status().isBadRequest());
        verify(brabo, never()).iniciar(any(), any(), any(), any(), anyBoolean());
    }
}
