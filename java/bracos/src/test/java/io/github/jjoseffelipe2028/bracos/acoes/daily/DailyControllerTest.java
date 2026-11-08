package io.github.jjoseffelipe2028.bracos.acoes.daily;

import static org.hamcrest.Matchers.matchesPattern;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

import io.github.jjoseffelipe2028.bracos.auditoria.Cliente;
import io.github.jjoseffelipe2028.bracos.erros.ErrosHandler;
import io.github.jjoseffelipe2028.bracos.seguranca.TokenInterceptor;
import io.github.jjoseffelipe2028.bracos.seguranca.WebConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** POST /acoes/daily de ponta a ponta, gravando numa pasta temporária. */
@WebMvcTest(DailyController.class)
@Import({DailyService.class, WebConfig.class, ErrosHandler.class})
@ExtendWith(OutputCaptureExtension.class)
class DailyControllerTest {

    private static final String TOKEN = "token-de-teste"; // valor FICTÍCIO
    private static final Path PASTA;

    static {
        try {
            PASTA = Files.createTempDirectory("daily-teste");
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void propriedades(DynamicPropertyRegistry registro) {
        registro.add("jarvis.token", () -> TOKEN);
        registro.add("jarvis.pastas.daily", PASTA::toString);
    }

    @Autowired
    private MockMvc mockMvc;

    private String json(String texto) {
        return "{\"texto\":\"" + texto + "\"}";
    }

    @Test
    void caminhoFeliz_200_gravaENaoDevolveCaminho(CapturedOutput saida) throws Exception {
        String hoje = LocalDate.now(DailyService.FUSO) + ".md";

        mockMvc.perform(post("/acoes/daily")
                        .header(TokenInterceptor.CABECALHO, TOKEN)
                        .header(Cliente.CABECALHO, "python")
                        .contentType(MediaType.APPLICATION_JSON).content(json("segredo do dia")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultado").value("ok"))
                .andExpect(jsonPath("$.arquivo").value(matchesPattern("\\d{4}-\\d{2}-\\d{2}\\.md")))
                // Só o nome do arquivo: a pasta (que mostra o usuário do Windows) não vaza
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString(PASTA.getFileName().toString()))));

        String nota = Files.readString(PASTA.resolve(hoje), StandardCharsets.UTF_8);
        assertTrue(nota.contains(" segredo do dia\n"));

        // Log de auditoria: quem, o quê, resultado. Sem o texto e sem o caminho.
        assertTrue(saida.getOut().contains("cliente=python acao=daily resultado=ok"));
        assertFalse(saida.getOut().contains("segredo do dia"), "texto do usuário não vai para o log");
        assertFalse(saida.getOut().contains(PASTA.toString()), "caminho não vai para o log");
    }

    @Test
    void semToken_401() throws Exception {
        mockMvc.perform(post("/acoes/daily")
                        .contentType(MediaType.APPLICATION_JSON).content(json("oi")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenErrado_401() throws Exception {
        mockMvc.perform(post("/acoes/daily")
                        .header(TokenInterceptor.CABECALHO, "token-errado")
                        .contentType(MediaType.APPLICATION_JSON).content(json("oi")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void textoVazio_400() throws Exception {
        mockMvc.perform(post("/acoes/daily")
                        .header(TokenInterceptor.CABECALHO, TOKEN)
                        .contentType(MediaType.APPLICATION_JSON).content(json("   ")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("entrada invalida"));
    }

    @Test
    void textoGigante_400() throws Exception {
        mockMvc.perform(post("/acoes/daily")
                        .header(TokenInterceptor.CABECALHO, TOKEN)
                        .contentType(MediaType.APPLICATION_JSON).content(json("a".repeat(2001))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void campoExtraDeCaminhoEIgnorado() throws Exception {
        // Tentativa de escolher o arquivo pelo pedido: o campo não existe no DailyRequest
        mockMvc.perform(post("/acoes/daily")
                        .header(TokenInterceptor.CABECALHO, TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"texto\":\"oi\",\"arquivo\":\"../../fora.md\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.arquivo").value(matchesPattern("\\d{4}-\\d{2}-\\d{2}\\.md")));
        assertFalse(Files.exists(PASTA.resolve("../../fora.md").normalize()));
    }

    @Test
    void jsonQuebrado_400() throws Exception {
        mockMvc.perform(post("/acoes/daily")
                        .header(TokenInterceptor.CABECALHO, TOKEN)
                        .contentType(MediaType.APPLICATION_JSON).content("{texto:"))
                .andExpect(status().isBadRequest());
    }
}
