package io.github.jjoseffelipe2028.bracos.seguranca;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.jjoseffelipe2028.bracos.acoes.AcoesController;
import io.github.jjoseffelipe2028.bracos.erros.ErrosHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** Token configurado (valor FICTÍCIO de teste). */
@WebMvcTest({AcoesController.class, RotaTesteController.class})
@Import({WebConfig.class, ErrosHandler.class})
@TestPropertySource(properties = "jarvis.token=token-de-teste")
class TokenInterceptorTest {

    private static final String JSON_OK = "{\"texto\":\"oi\"}";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void semToken_retorna401() throws Exception {
        mockMvc.perform(post("/acoes/teste-token").contentType(MediaType.APPLICATION_JSON).content(JSON_OK))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenErrado_retorna401() throws Exception {
        mockMvc.perform(post("/acoes/teste-token").header("X-Jarvis-Token", "errado")
                .contentType(MediaType.APPLICATION_JSON).content(JSON_OK))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenCerto_passa() throws Exception {
        mockMvc.perform(post("/acoes/teste-token").header("X-Jarvis-Token", "token-de-teste")
                .contentType(MediaType.APPLICATION_JSON).content(JSON_OK))
                .andExpect(status().isOk());
    }

    @Test
    void erroDeValidacao_retorna400SemStackTrace() throws Exception {
        mockMvc.perform(post("/acoes/teste-token").header("X-Jarvis-Token", "token-de-teste")
                .contentType(MediaType.APPLICATION_JSON).content("{\"texto\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("entrada invalida")))
                .andExpect(content().string(not(containsString("Exception"))))
                .andExpect(content().string(not(containsString("at io.github"))));
    }

    @Test
    void getHora_continuaAbertoSemToken() throws Exception {
        mockMvc.perform(get("/acoes/hora")).andExpect(status().isOk());
    }
}
