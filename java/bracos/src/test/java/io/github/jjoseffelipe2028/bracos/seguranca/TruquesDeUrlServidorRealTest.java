package io.github.jjoseffelipe2028.bracos.seguranca;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Achado 1 do jarvis-hacker (Ticket 003): truques de URL num servidor DE VERDADE (Tomcat),
 * não só no MockMvc. Nenhuma variação pode ler as pendências sem token. 🔒
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TruquesDeUrlServidorRealTest {

    private static final String TOKEN = "token-de-teste"; // valor FICTÍCIO
    private static final String SEGREDO = "tarefa secreta do servidor real";
    private static final Path PASTA;

    static {
        try {
            PASTA = Files.createTempDirectory("truques-url");
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void propriedades(DynamicPropertyRegistry registro) {
        registro.add("jarvis.token", () -> TOKEN);
        registro.add("jarvis.pastas.daily", PASTA::toString);
    }

    @BeforeAll
    static void criaDaily() throws IOException {
        Files.writeString(PASTA.resolve(LocalDate.now(java.time.ZoneId.of("America/Sao_Paulo")) + ".md"),
                "- [ ] " + SEGREDO + "\n", StandardCharsets.UTF_8);
    }

    @LocalServerPort
    private int porta;

    private final HttpClient http = HttpClient.newHttpClient();

    private HttpResponse<String> get(String caminho, String token) throws Exception {
        HttpRequest.Builder req = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + porta + caminho)).GET();
        if (token != null) {
            req.header(TokenInterceptor.CABECALHO, token);
        }
        return http.send(req.build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void comToken_oServidorRealDevolveAPendencia() throws Exception {
        // Controle: prova que o segredo ESTÁ lá. Sem isto, o teste abaixo poderia passar por engano.
        HttpResponse<String> r = get("/acoes/pendencias", TOKEN);
        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains(SEGREDO));
    }

    @Test
    void nenhumTruqueDeUrlLeSemToken() throws Exception {
        List<String> truques = List.of(
                "/acoes/pendencias",
                "/acoes/pendencias/",
                "/acoes//pendencias",
                "/acoes/pendencias;x=1",
                "/acoes/hora;x/../pendencias",
                "/acoes/hora/../pendencias",
                "/acoes/%70endencias",
                "/acoes%2Fpendencias",
                "/ACOES/pendencias",
                "/acoes/PENDENCIAS");
        for (String caminho : truques) {
            HttpResponse<String> r = get(caminho, null);
            assertTrue(Set.of(400, 401, 404).contains(r.statusCode()), caminho + " → " + r.statusCode());
            assertFalse(r.body().contains(SEGREDO), caminho + " vazou a pendência sem token");
        }
    }

    @Test
    void horaContinuaPublica() throws Exception {
        assertEquals(200, get("/acoes/hora", null).statusCode());
    }
}
