package io.github.jjoseffelipe2028.bracos.central;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
 * Central (Tickets 014 a 018): a página abre, é 100% offline e tem as travas de segurança. 🔒
 */
@WebMvcTest(AcoesController.class)
@Import({WebConfig.class, ErrosHandler.class})
@TestPropertySource(properties = "jarvis.token=token-de-teste") // valor FICTÍCIO
class CentralPaginaTest {

    private static final String[] ARQUIVOS = {"index.html", "central.css", "central.js", "escritorio.js"};

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
    @ValueSource(strings = {"/index.html", "/central.css", "/central.js", "/escritorio.js"})
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

    @ParameterizedTest
    @ValueSource(strings = {"central.js", "escritorio.js"})
    void jsNaoTransformaRespostaEmCodigo(String arquivo) throws IOException {
        String js = ler(arquivo);
        for (String proibido : new String[] {"innerHTML", "outerHTML", "insertAdjacentHTML", "eval(", "new Function", "document.write"}) {
            assertFalse(js.contains(proibido), arquivo + " usa " + proibido);
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

    @Test
    void escritorioNaoFalaComARedeSozinho() throws IOException {
        String js = ler("escritorio.js");
        for (String proibido : new String[] {"fetch(", "XMLHttpRequest", "WebSocket", "EventSource", "sendBeacon",
                "X-Jarvis-Token", "document.cookie", "localStorage", "sessionStorage", "new Image", "drawImage"}) {
            assertFalse(js.contains(proibido), "escritorio.js usa " + proibido);
        }
        assertTrue(js.contains("pedir("), "as ações reais passam pelo pedir() da central.js (crachá)");
        assertTrue(js.contains("prepararPedido()"), "001/002 só preparam o pedido no cartão de aceite");
        assertTrue(js.contains("/central/acoes/eventos"), "o escritório ouve o quadro de eventos (Ticket 017)");
    }

    @Test
    void vozSoLocal_comecaMuda_eSoFrasesFixas() throws IOException {
        String js = ler("escritorio.js");
        assertTrue(js.contains("speechSynthesis"), "a voz usa a fala do próprio navegador (Ticket 018)");
        assertTrue(js.contains("localService === true"), "só voz local: voz online manda o texto para a nuvem");
        assertTrue(js.contains("let vozLigada = false"), "a voz começa desligada");
        assertFalse(js.contains("falar(e."), "a voz nunca fala texto vindo da API");
        String html = ler("index.html");
        assertTrue(html.contains("id=\"esc-voz\"") && html.contains("aria-pressed=\"false\""), "botão de voz começa mudo");
    }

    @Test
    void dailyPelaCentral_soComConfirmar_eSoPorUmLugar() throws IOException {
        String js = ler("central.js");
        String html = ler("index.html");
        // Ticket 020: escrever no cofre exige Revisar → Confirmar
        assertTrue(html.contains("id=\"bt-daily-confirmar\"") && html.contains("id=\"bt-daily-cancelar\""), "falta a confirmação");
        assertTrue(html.contains("id=\"daily-cartao\" class=\"cartao cartao-daily\" hidden"), "o cartão começa escondido");
        assertTrue(html.contains("maxlength=\"2000\""), "mesmo limite da API");
        assertTrue(js.contains("enviar(\"/central/acoes/daily\""), "o POST sai só da central.js");
        // Só 2 POSTs na Central inteira: o crachá (/central/sessao) e o daily (enviar)
        assertEquals(2, ocorrencias(js, "method: \"POST\""), "POST novo na Central precisa de ticket");
        String esc = ler("escritorio.js");
        assertTrue(esc.contains("abrirDaily()"), "a skill do escritório só abre o campo");
        assertFalse(esc.contains("enviar("), "o escritório nunca escreve sozinho");
    }

    @Test
    void golpePelaCentral_soMandaOTexto_eONivelEConferido() throws IOException {
        String js = ler("central.js");
        String html = ler("index.html");
        // Ticket 023: campo com o mesmo limite da API; envio só pelo enviar() que já existe
        assertTrue(html.contains("id=\"golpe-texto\" maxlength=\"4000\""), "mesmo limite da API");
        assertTrue(html.contains("id=\"golpe\" class=\"nuvem\" hidden"), "o campo começa escondido");
        assertTrue(js.contains("enviar(\"/central/acoes/sentinela/golpe\", { texto })"), "manda só o texto");
        // Só 2 chamadas de enviar(): daily e golpe. Qualquer outra precisa de ticket.
        assertEquals(2, ocorrencias(js, "enviar(\""), "envio novo na Central precisa de ticket");
        assertTrue(js.contains("Object.hasOwn(NIVEL_GOLPE, r.nivel)"), "nível fora da lista é recusado");
        assertTrue(ler("escritorio.js").contains("abrirGolpe()"), "a skill do escritório só abre o campo");
    }

    private static int ocorrencias(String texto, String trecho) {
        int n = 0;
        for (int i = texto.indexOf(trecho); i >= 0; i = texto.indexOf(trecho, i + 1)) {
            n++;
        }
        return n;
    }

    @Test
    void escritorioCarregaDepoisDaCentral_eSemEstiloEmbutido() throws IOException {
        String html = ler("index.html");
        int central = html.indexOf("<script src=\"central.js\">");
        int escritorio = html.indexOf("<script src=\"escritorio.js\">");
        assertTrue(central >= 0 && escritorio > central, "escritorio.js precisa vir depois da central.js");
        assertFalse(Pattern.compile("\\sstyle=").matcher(html).find(), "estilo embutido: a CSP bloqueia");
        assertTrue(html.contains("id=\"esc-cena\""), "falta o palco do escritório");
    }
}
