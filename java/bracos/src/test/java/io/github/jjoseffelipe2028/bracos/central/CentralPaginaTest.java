package io.github.jjoseffelipe2028.bracos.central;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

import io.github.jjoseffelipe2028.bracos.acoes.AcoesController;
import io.github.jjoseffelipe2028.bracos.erros.ErrosHandler;
import io.github.jjoseffelipe2028.bracos.seguranca.WebConfig;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Central v0 (Ticket 014): a página abre, é 100% offline e tem as travas de segurança. 🔒
 */
@WebMvcTest(AcoesController.class)
@Import({WebConfig.class, ErrosHandler.class})
@TestPropertySource(properties = "jarvis.token=token-de-teste") // valor FICTÍCIO
class CentralPaginaTest {

    private static final String[] ARQUIVOS = {"index.html", "central.css", "central.js"};

    @Autowired
    private MockMvc mockMvc;

    private static String ler(String arquivo) throws IOException {
        try (InputStream in = CentralPaginaTest.class.getResourceAsStream("/static/" + arquivo)) {
            assertTrue(in != null, "arquivo da Central sumiu: " + arquivo);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void raizAbreACentral() throws Exception {
        mockMvc.perform(get("/")).andExpect(forwardedUrl("index.html"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/index.html", "/central.css", "/central.js"})
    void arquivosSaoServidosSemToken(String rota) throws Exception {
        mockMvc.perform(get(rota)).andExpect(status().isOk());
    }

    @Test
    void paginaTemAMarcaDoJarvis() throws Exception {
        mockMvc.perform(get("/index.html"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("J.A.R.V.I.S")));
    }

    @Test
    void nadaVemDaInternet_funcionaOffline() throws IOException {
        // Nenhum endereço externo: nem fonte, nem biblioteca, nem imagem
        Pattern externo = Pattern.compile("(?i)(https?:)?//[a-z0-9.-]+\\.[a-z]{2,}");
        for (String arquivo : ARQUIVOS) {
            assertFalse(externo.matcher(ler(arquivo)).find(), arquivo + " aponta para a internet");
        }
    }

    @Test
    void paginaTemCspTrancada() throws IOException {
        String html = ler("index.html");
        assertTrue(html.contains("Content-Security-Policy"), "falta a trava CSP");
        assertTrue(html.contains("default-src 'self'"), "CSP precisa bloquear tudo de fora");
        assertTrue(html.contains("script-src 'self'"), "só scripts do próprio Jarvis");
        assertFalse(html.contains("unsafe-inline") || html.contains("unsafe-eval"), "CSP afrouxada");
        assertFalse(Pattern.compile("<script(?![^>]*\\bsrc=)").matcher(html).find(), "script embutido no HTML");
    }

    @Test
    void jsNaoTransformaRespostaEmCodigo() throws IOException {
        String js = ler("central.js");
        for (String proibido : new String[] {"innerHTML", "outerHTML", "insertAdjacentHTML", "eval(", "new Function", "document.write"}) {
            assertFalse(js.contains(proibido), "central.js usa " + proibido);
        }
    }

    @Test
    void jsNuncaMexeComToken() throws IOException {
        String js = ler("central.js");
        for (String cabecalho : new String[] {"X-Jarvis-Token", "X-Jarvis-Assinatura", "X-Jarvis-Nonce"}) {
            assertFalse(js.contains(cabecalho), "a Central não pode mandar " + cabecalho);
        }
        assertFalse(js.contains("document.cookie"), "o crachá é HttpOnly: o JS nem tenta ler");
        assertTrue(js.contains("history.replaceState"), "a chave de uso único some do endereço");
        assertFalse(js.contains("localStorage") || js.contains("sessionStorage"), "nada guardado no navegador");
        assertTrue(js.contains("credentials: \"omit\""), "fetch sem cookies");
    }
}
