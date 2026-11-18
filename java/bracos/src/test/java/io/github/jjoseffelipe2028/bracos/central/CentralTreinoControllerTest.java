package io.github.jjoseffelipe2028.bracos.central;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import io.github.jjoseffelipe2028.bracos.acoes.git.GitStatusService;
import io.github.jjoseffelipe2028.bracos.acoes.pendencias.PendenciasService;
import io.github.jjoseffelipe2028.bracos.acoes.treino.TreinoService;
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

/** Ticket 064: a Sala de Treino de ponta a ponta, com o TreinoService DE VERDADE (porteiros reais). 🥋 */
@WebMvcTest({CentralTreinoController.class, CentralController.class})
@Import({TreinoService.class, ChaveDaCentral.class, SessoesDaCentral.class, QuadroDeEventos.class, WebConfig.class, ErrosHandler.class})
@ExtendWith(OutputCaptureExtension.class)
class CentralTreinoControllerTest {

    private static final String ORIGEM = "http://localhost:80";
    private static final Path ARQUIVO;

    static {
        try {
            ARQUIVO = Files.createTempDirectory("central-treino-teste").resolve(".central-chave");
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

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder treino(Cookie cracha, String ataque) {
        var p = post("/central/acoes/treino").header(CentralInterceptor.CABECALHO, "1").header("Origin", ORIGEM)
                .contentType(MediaType.APPLICATION_JSON).content("{\"ataque\":\"" + ataque + "\"}");
        return cracha == null ? p : p.cookie(cracha);
    }

    @Test
    void osQuatroAtaques_deVerdade_saoBarrados(CapturedOutput saida) throws Exception {
        Cookie cracha = entrar();
        for (String ataque : new String[] {"csrf", "rebinding", "sem-cracha", "token-falso"}) {
            mockMvc.perform(treino(cracha, ataque))
                    .andExpect(status().isOk()) // a SIMULAÇÃO deu certo; o ataque dentro dela foi barrado
                    .andExpect(jsonPath("$.barrado").value(true))
                    .andExpect(jsonPath("$.ataque").value(ataque));
        }
        assertTrue(saida.getOut().contains("acao=treino ataque=rebinding resultado=barrado porteiro=HostInterceptor"));
        assertFalse(saida.getOut().contains("defesa-falhou"));
    }

    @Test
    void ataqueForaDaLista_400_eSemCracha_401() throws Exception {
        Cookie cracha = entrar();
        mockMvc.perform(treino(cracha, "sqli")).andExpect(status().isBadRequest());
        mockMvc.perform(treino(cracha, "SEM_CRACHA")).andExpect(status().isBadRequest());
        mockMvc.perform(treino(null, "csrf")).andExpect(status().isUnauthorized());
    }
}
