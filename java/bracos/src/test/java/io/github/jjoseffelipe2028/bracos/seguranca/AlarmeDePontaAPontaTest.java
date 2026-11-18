package io.github.jjoseffelipe2028.bracos.seguranca;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.jjoseffelipe2028.bracos.acoes.sentinela.DetectorDeGolpe;
import io.github.jjoseffelipe2028.bracos.acoes.sentinela.IntegridadeService;
import io.github.jjoseffelipe2028.bracos.acoes.sentinela.PortasService;
import io.github.jjoseffelipe2028.bracos.acoes.sentinela.SentinelaController;
import io.github.jjoseffelipe2028.bracos.acoes.sentinela.VerificadorDePropostas;
import io.github.jjoseffelipe2028.bracos.central.QuadroDeEventos;
import io.github.jjoseffelipe2028.bracos.erros.ErrosHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Ticket 080 de ponta a ponta: 5 tokens errados em seguida → o alarme dispara e a Sentinela mostra. Tokens FICTÍCIOS. */
@WebMvcTest(SentinelaController.class)
@Import({QuadroDeEventos.class, DetectorDeGolpe.class, VerificadorDePropostas.class, WebConfig.class,
        ErrosHandler.class, AlarmeDeTentativas.class})
@TestPropertySource(properties = "jarvis.token=token-de-teste")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD) // cada teste começa com 0 disparos
@ExtendWith(OutputCaptureExtension.class)
class AlarmeDePontaAPontaTest {

    private static final String REAL = "token-de-teste";
    private static final String CHUTE = "chute-do-atacante-123";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PortasService portas;

    @MockitoBean
    private IntegridadeService integridade;

    @Test
    void cincoTokensErrados_disparaOAlarme_semPorNadaDoAtacanteNoLog(CapturedOutput saida) throws Exception {
        mockMvc.perform(get("/acoes/sentinela/alarme").header(TokenInterceptor.CABECALHO, REAL))
                .andExpect(status().isOk()).andExpect(jsonPath("$.disparos").value(0));
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(get("/acoes/sentinela/portas").header(TokenInterceptor.CABECALHO, CHUTE))
                    .andExpect(status().isUnauthorized());
        }
        mockMvc.perform(get("/acoes/sentinela/alarme").header(TokenInterceptor.CABECALHO, REAL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.disparos").value(1))
                .andExpect(jsonPath("$.ultimoDisparo").isNotEmpty());
        assertTrue(saida.getOut().contains("alarme=tentativas negados=5 janela=60s disparos=1"));
        assertFalse(saida.getOut().contains(CHUTE), "o token chutado não vai para o log");
    }

    @Test
    void hostEstranho_tambemConta() throws Exception {
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(get("/acoes/sentinela/portas").header("Host", "evil.com"))
                    .andExpect(status().isForbidden());
        }
        mockMvc.perform(get("/acoes/sentinela/alarme").header(TokenInterceptor.CABECALHO, REAL))
                .andExpect(jsonPath("$.disparos").value(1));
    }

    @Test
    void pedidosCertos_naoContam() throws Exception {
        for (int i = 0; i < 6; i++) {
            mockMvc.perform(get("/acoes/sentinela/alarme").header(TokenInterceptor.CABECALHO, REAL))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.disparos").value(0));
        }
    }

    @Test
    void verOAlarme_semToken_401() throws Exception {
        mockMvc.perform(get("/acoes/sentinela/alarme")).andExpect(status().isUnauthorized());
    }
}
