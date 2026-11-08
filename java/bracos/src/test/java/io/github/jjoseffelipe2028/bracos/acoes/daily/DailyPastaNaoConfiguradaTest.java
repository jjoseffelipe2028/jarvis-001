package io.github.jjoseffelipe2028.bracos.acoes.daily;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.jjoseffelipe2028.bracos.erros.ErrosHandler;
import io.github.jjoseffelipe2028.bracos.seguranca.TokenInterceptor;
import io.github.jjoseffelipe2028.bracos.seguranca.WebConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** Token certo, mas JARVIS_PASTA_DAILY vazio → 503 (falha fechada). */
@WebMvcTest(DailyController.class)
@Import({DailyService.class, WebConfig.class, ErrosHandler.class})
@TestPropertySource(properties = {"jarvis.token=token-de-teste", "jarvis.pastas.daily="})
class DailyPastaNaoConfiguradaTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void pastaVazia_503() throws Exception {
        mockMvc.perform(post("/acoes/daily")
                        .header(TokenInterceptor.CABECALHO, "token-de-teste")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"texto\":\"oi\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.erro").value("pasta nao configurada"));
    }
}
