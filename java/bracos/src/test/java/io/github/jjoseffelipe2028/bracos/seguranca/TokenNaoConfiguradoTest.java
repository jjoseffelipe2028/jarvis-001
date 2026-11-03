package io.github.jjoseffelipe2028.bracos.seguranca;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** Token NÃO configurado: tudo recusado com 503 (falha fechada). */
@WebMvcTest(RotaTesteController.class)
@Import(WebConfig.class)
@TestPropertySource(properties = "jarvis.token=")
class TokenNaoConfiguradoTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void tokenNaoConfigurado_retorna503_mesmoComCabecalho() throws Exception {
        mockMvc.perform(post("/acoes/teste-token").header("X-Jarvis-Token", "qualquer")
                .contentType(MediaType.APPLICATION_JSON).content("{\"texto\":\"oi\"}"))
                .andExpect(status().isServiceUnavailable());
    }
}
