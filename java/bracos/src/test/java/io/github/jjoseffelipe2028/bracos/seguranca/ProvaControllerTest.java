package io.github.jjoseffelipe2028.bracos.seguranca;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;

import io.github.jjoseffelipe2028.bracos.erros.ErrosHandler;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** GET /acoes/prova: o "aperto de mão" que prova que esta API conhece o token. */
@WebMvcTest(ProvaController.class)
@Import({WebConfig.class, ErrosHandler.class})
@TestPropertySource(properties = "jarvis.token=token-de-teste") // valor FICTÍCIO
class ProvaControllerTest {

    private static final String DESAFIO = "00112233445566778899aabbccddeeff";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void vetorConhecido_mesmoValorQueOPythonCalcula() {
        // hmac.new(b"token-de-teste", b"jarvis-servidor:" + DESAFIO, sha256).hexdigest()
        // (o mesmo vetor está em python/tests/test_bracos.py)
        String prova = ProvaController.hmac("token-de-teste".getBytes(StandardCharsets.UTF_8),
                ProvaController.PREFIXO + DESAFIO);
        assertEquals(VETOR, prova);
    }

    @Test
    void semToken_respondeAProva() throws Exception {
        mockMvc.perform(get("/acoes/prova").param("desafio", DESAFIO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.prova").value(VETOR))
                // a resposta nunca contém o token
                .andExpect(content().string(Matchers.not(Matchers.containsString("token-de-teste"))));
    }

    @Test
    void desafiosDiferentes_provasDiferentes() {
        byte[] chave = "token-de-teste".getBytes(StandardCharsets.UTF_8);
        assertNotEquals(ProvaController.hmac(chave, ProvaController.PREFIXO + DESAFIO),
                ProvaController.hmac(chave, ProvaController.PREFIXO + DESAFIO.replace('0', '1')));
    }

    @Test
    void desafioInvalido_400() throws Exception {
        for (String ruim : new String[] {"", "abc", "ZZ112233445566778899aabbccddeeff", "a".repeat(129),
                DESAFIO + "\n", "../../etc/passwd" + DESAFIO}) {
            mockMvc.perform(get("/acoes/prova").param("desafio", ruim))
                    .andExpect(status().isBadRequest());
        }
        mockMvc.perform(get("/acoes/prova")).andExpect(status().isBadRequest());
    }

    /** Calculado pelo PYTHON (hmac + sha256), não pelo Java: prova que os dois lados batem. */
    /** Ticket 008: rajada de provas é cortada (anti abuso da rota pública). */
    @Test
    void rajadaDeProvas_eCortada() throws Exception {
        int cortados = 0;
        for (int i = 0; i < ProvaController.LIMITE_POR_SEGUNDO * 2; i++) {
            int st = mockMvc.perform(get("/acoes/prova").param("desafio", DESAFIO))
                    .andReturn().getResponse().getStatus();
            if (st == 503) {
                cortados++;
            }
        }
        org.junit.jupiter.api.Assertions.assertTrue(cortados > 0, "nenhum pedido foi cortado");
        Thread.sleep(1100); // deixa o segundo virar: os outros testes começam com o limite zerado
    }

    static final String VETOR = "74442c56051022c7ea28f29be0b09f933b266ac97dd82705087ec3ebec323f12";
}
