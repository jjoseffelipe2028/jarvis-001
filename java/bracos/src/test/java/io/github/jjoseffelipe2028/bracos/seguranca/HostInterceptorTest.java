package io.github.jjoseffelipe2028.bracos.seguranca;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.jjoseffelipe2028.bracos.acoes.AcoesController;
import io.github.jjoseffelipe2028.bracos.erros.ErrosHandler;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** DNS rebinding: só localhost e 127.0.0.1 passam, em GET e em POST. */
@WebMvcTest({AcoesController.class, RotaTesteController.class})
@Import({WebConfig.class, ErrosHandler.class})
@TestPropertySource(properties = "jarvis.token=token-de-teste")
class HostInterceptorTest {

    @Autowired
    private MockMvc mockMvc;

    @ParameterizedTest(name = "Host {0} passa")
    @ValueSource(strings = {"localhost:8080", "127.0.0.1:8080", "LOCALHOST:8080", "localhost"})
    void hostLocal_passa(String host) throws Exception {
        mockMvc.perform(get("/acoes/hora").header("Host", host))
                .andExpect(status().isOk());
    }

    @ParameterizedTest(name = "Host {0} é barrado (GET)")
    @ValueSource(strings = {"evil.com", "evil.com:8080", "127.0.0.1.evil.com:8080", "localhost.evil.com",
            "192.168.0.10:8080", "0.0.0.0:8080"})
    void hostDeFora_get_403(String host) throws Exception {
        mockMvc.perform(get("/acoes/hora").header("Host", host))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.erro").value("host nao permitido"));
    }

    /** X-Forwarded-Host é ignorado: não dá para "enganar" o filtro com ele (achado 8). */
    @org.junit.jupiter.api.Test
    void xForwardedHostEIgnorado() throws Exception {
        mockMvc.perform(get("/acoes/hora").header("Host", "localhost:8080").header("X-Forwarded-Host", "evil.com"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/acoes/hora").header("Host", "evil.com").header("X-Forwarded-Host", "localhost"))
                .andExpect(status().isForbidden());
    }

    @ParameterizedTest(name = "Host {0} é barrado (POST, mesmo com token certo)")
    @ValueSource(strings = {"evil.com:8080", "attacker.localhost.com"})
    void hostDeFora_post_403(String host) throws Exception {
        mockMvc.perform(post("/acoes/teste-token").header("Host", host)
                        .header(TokenInterceptor.CABECALHO, "token-de-teste")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"texto\":\"oi\"}"))
                .andExpect(status().isForbidden());
    }
}
