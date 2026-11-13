package io.github.jjoseffelipe2028.bracos.seguranca;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.concurrent.ThreadLocalRandom;

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
import org.springframework.test.web.servlet.MvcResult;

/**
 * Ticket 034 de ponta a ponta: o token-isca NUNCA abre a porta, dispara o alarme e não se entrega. 🪤
 * Tokens FICTÍCIOS.
 */
@WebMvcTest(SentinelaController.class)
@Import({QuadroDeEventos.class, DetectorDeGolpe.class, VerificadorDePropostas.class, Isca.class, WebConfig.class,
        ErrosHandler.class, CorpoGuardadoFilter.class})
@TestPropertySource(properties = {"jarvis.token=token-de-teste", "jarvis.isca.token=isca-falsa-de-teste"})
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD) // cada teste começa com 0 toques
@ExtendWith(OutputCaptureExtension.class)
class IscaDePontaAPontaTest {

    private static final String REAL = "token-de-teste";
    private static final String ISCA = "isca-falsa-de-teste";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PortasService portas;

    @MockitoBean
    private IntegridadeService integridade;

    private int toques() throws Exception {
        MvcResult r = mockMvc.perform(get("/acoes/sentinela/isca").header(TokenInterceptor.CABECALHO, REAL))
                .andExpect(status().isOk()).andExpect(jsonPath("$.ligada").value(true)).andReturn();
        String corpo = r.getResponse().getContentAsString();
        return Integer.parseInt(corpo.replaceAll(".*\"toques\":(\\d+).*", "$1"));
    }

    @Test
    void tokenIscaNoCabecalho_401_contaOToque_eGritaNoLog(CapturedOutput saida) throws Exception {
        assertEquals(0, toques());
        MvcResult comIsca = mockMvc.perform(get("/acoes/sentinela/portas").header(TokenInterceptor.CABECALHO, ISCA))
                .andExpect(status().isUnauthorized()).andReturn();
        assertEquals(1, toques());
        assertTrue(saida.getOut().contains("motivo=ISCA_TOCADA"));
        assertFalse(saida.getOut().contains(ISCA), "o token-isca não vai para o log");

        // O invasor não pode perceber a armadilha: a resposta é IGUAL à de um token errado qualquer
        MvcResult comErrado = mockMvc.perform(get("/acoes/sentinela/portas").header(TokenInterceptor.CABECALHO, "chute"))
                .andExpect(status().isUnauthorized()).andReturn();
        assertEquals(comErrado.getResponse().getContentAsString(), comIsca.getResponse().getContentAsString());
        assertEquals(1, toques(), "token errado comum não é toque na isca");
    }

    @Test
    void pedidoAssinadoComOTokenIsca_401_eContaOToque() throws Exception {
        String hora = String.valueOf(System.currentTimeMillis() / 1000);
        byte[] b = new byte[16];
        ThreadLocalRandom.current().nextBytes(b);
        String nonce = HexFormat.of().formatHex(b);
        byte[] sub = ProvaController.hmacBytes(ISCA.getBytes(StandardCharsets.UTF_8), Assinatura.ROTULO_SUBCHAVE);
        String assinatura = Assinatura.assinar(sub, "GET", "/acoes/sentinela/portas", hora, nonce, new byte[0]);

        mockMvc.perform(get("/acoes/sentinela/portas").header(Assinatura.CAB_HORA, hora)
                        .header(Assinatura.CAB_NONCE, nonce).header(Assinatura.CAB_ASSINATURA, assinatura))
                .andExpect(status().isUnauthorized());
        assertEquals(1, toques());
    }

    @Test
    void tokenDeVerdade_passa_eNaoTocaNaIsca() throws Exception {
        mockMvc.perform(get("/acoes/sentinela/isca").header(TokenInterceptor.CABECALHO, REAL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.toques").value(0))
                .andExpect(jsonPath("$.ultimoToque").value(""))
                .andExpect(jsonPath("$.arquivo").value("sem"));
    }

    @Test
    void aRotaDaIscaTambemExigeToken() throws Exception {
        mockMvc.perform(get("/acoes/sentinela/isca")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/acoes/sentinela/isca").header(TokenInterceptor.CABECALHO, ISCA))
                .andExpect(status().isUnauthorized());
    }
}
