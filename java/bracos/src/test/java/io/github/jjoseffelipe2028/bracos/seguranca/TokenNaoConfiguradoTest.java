package io.github.jjoseffelipe2028.bracos.seguranca;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** Token NÃO configurado: tudo recusado com 503 (falha fechada). */
@WebMvcTest(RotaTesteController.class)
@Import(WebConfig.class)
@TestPropertySource(properties = "jarvis.token=")
@ExtendWith(OutputCaptureExtension.class)
class TokenNaoConfiguradoTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void tokenNaoConfigurado_retorna503_mesmoComCabecalho() throws Exception {
        mockMvc.perform(post("/acoes/teste-token").header(TokenInterceptor.CABECALHO, "qualquer")
                .contentType(MediaType.APPLICATION_JSON).content("{\"texto\":\"oi\"}"))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void tokenNaoConfigurado_semCabecalho_retorna503() throws Exception {
        mockMvc.perform(post("/acoes/teste-token")
                .contentType(MediaType.APPLICATION_JSON).content("{\"texto\":\"oi\"}"))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void motivo503_tambemSanitiza(CapturedOutput output) throws Exception {
        mockMvc.perform(post("/acoes/x").with(req -> {
            req.setRequestURI("/acoes/x\r\n\u001bacao=x resultado=ok");
            return req;
        })).andExpect(status().isServiceUnavailable());

        String log = output.getAll();
        int ini = log.indexOf("acao=");
        int fim = log.indexOf("motivo=token_nao_configurado");
        assertTrue(ini >= 0 && fim > ini, "log sem a linha de 503: " + log);
        String trecho = log.substring(ini, fim);
        assertFalse(trecho.contains("\r"));
        assertFalse(trecho.contains("\n"));
        assertFalse(trecho.contains("\u001b"));
        assertTrue(trecho.contains("_"), "trecho: " + trecho);
    }
}
