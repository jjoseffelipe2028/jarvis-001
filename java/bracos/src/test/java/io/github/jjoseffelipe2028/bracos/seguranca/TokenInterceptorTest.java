package io.github.jjoseffelipe2028.bracos.seguranca;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.util.stream.Stream;

import io.github.jjoseffelipe2028.bracos.acoes.AcoesController;
import io.github.jjoseffelipe2028.bracos.erros.ErrosHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Token configurado (valor FICTÍCIO de teste). */
@WebMvcTest({AcoesController.class, RotaTesteController.class})
@Import({WebConfig.class, ErrosHandler.class})
@TestPropertySource(properties = "jarvis.token=token-de-teste")
@ExtendWith(OutputCaptureExtension.class)
class TokenInterceptorTest {

    private static final String TOKEN_CERTO = "token-de-teste";
    private static final String JSON_OK = "{\"texto\":\"oi\"}";

    @Autowired
    private MockMvc mockMvc;

    static Stream<Arguments> tokensInvalidos() {
        return Stream.of(
                Arguments.of("sem cabecalho", null),
                Arguments.of("cabecalho vazio", ""),
                Arguments.of("mesmo tamanho, diferente", "token-de-testX"),
                Arguments.of("token curto", "x"),
                Arguments.of("token muito longo", "a".repeat(5000)));
    }

    @ParameterizedTest(name = "{0} retorna 401")
    @MethodSource("tokensInvalidos")
    void tokenInvalido_retorna401(String descricao, String valor) throws Exception {
        MockHttpServletRequestBuilder req = post("/acoes/teste-token")
                .contentType(MediaType.APPLICATION_JSON).content(JSON_OK);
        if (valor != null) {
            req.header(TokenInterceptor.CABECALHO, valor);
        }
        mockMvc.perform(req).andExpect(status().isUnauthorized());
    }

    @Test
    void tokenCerto_passa() throws Exception {
        mockMvc.perform(post("/acoes/teste-token").header(TokenInterceptor.CABECALHO, TOKEN_CERTO)
                .contentType(MediaType.APPLICATION_JSON).content(JSON_OK))
                .andExpect(status().isOk());
    }

    @Test
    void postEmRotaInexistenteSemToken_retorna401() throws Exception {
        mockMvc.perform(post("/acoes/nao-existe").contentType(MediaType.APPLICATION_JSON).content(JSON_OK))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void postEmRotaInexistenteComToken_retorna404() throws Exception {
        mockMvc.perform(post("/acoes/nao-existe").header(TokenInterceptor.CABECALHO, TOKEN_CERTO)
                .contentType(MediaType.APPLICATION_JSON).content(JSON_OK))
                .andExpect(status().isNotFound());
    }

    @Test
    void erroDeValidacao_retorna400SemStackTrace() throws Exception {
        mockMvc.perform(post("/acoes/teste-token").header(TokenInterceptor.CABECALHO, TOKEN_CERTO)
                .contentType(MediaType.APPLICATION_JSON).content("{\"texto\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("entrada invalida")))
                .andExpect(content().string(not(containsString("Exception"))))
                .andExpect(content().string(not(containsString("at io.github"))));
    }

    @Test
    void entradaGigante_retorna400() throws Exception {
        String gigante = "{\"texto\":\"" + "a".repeat(10000) + "\"}";
        mockMvc.perform(post("/acoes/teste-token").header(TokenInterceptor.CABECALHO, TOKEN_CERTO)
                .contentType(MediaType.APPLICATION_JSON).content(gigante))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("entrada invalida")));
    }

    @Test
    void jsonMalformado_retorna400SemStackTrace() throws Exception {
        mockMvc.perform(post("/acoes/teste-token").header(TokenInterceptor.CABECALHO, TOKEN_CERTO)
                .contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("entrada invalida")))
                .andExpect(content().string(not(containsString("Exception"))))
                .andExpect(content().string(not(containsString("at io.github"))));
    }

    @Test
    void corpoAusente_retorna400() throws Exception {
        mockMvc.perform(post("/acoes/teste-token").header(TokenInterceptor.CABECALHO, TOKEN_CERTO)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getHora_continuaAbertoSemToken() throws Exception {
        mockMvc.perform(get("/acoes/hora")).andExpect(status().isOk());
    }

    @Test
    void logNaoVazaToken(CapturedOutput output) throws Exception {
        String enviado = "token-enviado-errado-123";
        mockMvc.perform(post("/acoes/teste-token").header(TokenInterceptor.CABECALHO, enviado)
                .contentType(MediaType.APPLICATION_JSON).content(JSON_OK))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/acoes/teste-token").header(TokenInterceptor.CABECALHO, TOKEN_CERTO)
                .contentType(MediaType.APPLICATION_JSON).content(JSON_OK))
                .andExpect(status().isOk());

        assertFalse(output.getAll().contains(TOKEN_CERTO), "log vazou o token certo");
        assertFalse(output.getAll().contains(enviado), "log vazou o token enviado");
    }

    @Test
    void uriComQuebraDeLinha_naoCriaLinhaFalsa(CapturedOutput output) throws Exception {
        mockMvc.perform(post(URI.create("/acoes/x%0d%0aacao=x%20resultado=ok")))
                .andExpect(status().isUnauthorized());

        String[] linhas = output.getAll().split("\\R");
        long negacoes = Stream.of(linhas).filter(l -> l.contains("resultado=negado")).count();
        assertEquals(1, negacoes, "deveria haver uma unica linha de negacao");
        for (String linha : linhas) {
            assertFalse(linha.startsWith("acao=x"), "linha falsa no log: " + linha);
            assertFalse(linha.contains("acao=x resultado=ok"), "linha falsa no log: " + linha);
        }
    }

    @Test
    void uriComCaractereDeControleCru_viraUnderscore(CapturedOutput output) throws Exception {
        mockMvc.perform(post("/acoes/x").with(req -> {
            req.setRequestURI("/acoes/x\r\n\u001bacao=x resultado=ok");
            return req;
        })).andExpect(status().isUnauthorized());

        String trecho = trechoDaAcao(output.getAll());
        assertFalse(trecho.contains("\r"));
        assertFalse(trecho.contains("\n"));
        assertFalse(trecho.contains("\u001b"));
        assertTrue(trecho.contains("_"), "trecho: " + trecho);
    }

    @Test
    void uriGigante_ficaLimitadaNoLog(CapturedOutput output) throws Exception {
        mockMvc.perform(post("/acoes/" + "a".repeat(5000)))
                .andExpect(status().isUnauthorized());

        assertTrue(output.getAll().contains("..."));
        assertFalse(output.getAll().contains("a".repeat(200)));
    }

    @Test
    void uriNormal_continuaLegivelNoLog(CapturedOutput output) throws Exception {
        mockMvc.perform(post("/acoes/daily")).andExpect(status().isUnauthorized());

        assertTrue(output.getAll().contains("acao=/acoes/daily"));
    }

    /** Pedaco do log entre "acao=" e " resultado=negado" (pode revelar quebra de linha). */
    private static String trechoDaAcao(String log) {
        int ini = log.indexOf("acao=");
        int fim = log.indexOf("resultado=negado");
        assertTrue(ini >= 0 && fim > ini, "log sem a linha de negacao: " + log);
        return log.substring(ini, fim);
    }
}
