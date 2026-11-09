package io.github.jjoseffelipe2028.bracos.acoes.pendencias;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
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
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** GET /acoes/pendencias de ponta a ponta, lendo uma pasta temporária. */
@WebMvcTest(PendenciasController.class)
@Import({PendenciasService.class, WebConfig.class, ErrosHandler.class})
@ExtendWith(OutputCaptureExtension.class)
class PendenciasControllerTest {

    private static final String TOKEN = "token-de-teste"; // valor FICTÍCIO
    private static final Path PASTA;

    static {
        try {
            PASTA = Files.createTempDirectory("pendencias-teste");
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
    static void criaDailyDeHoje() throws IOException {
        Path hoje = PASTA.resolve(LocalDate.now(PendenciasService.FUSO) + ".md");
        Files.writeString(hoje, "- [ ] tarefa secreta\n- [x] feita\n", StandardCharsets.UTF_8);
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    void comToken_200_devolvePendenciasSemCaminho(CapturedOutput saida) throws Exception {
        mockMvc.perform(get("/acoes/pendencias")
                        .header(TokenInterceptor.CABECALHO, TOKEN)
                        .header(Cliente.CABECALHO, "python"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.ignorados").value(0))
                .andExpect(jsonPath("$.pendencias[0].texto").value("tarefa secreta"))
                .andExpect(jsonPath("$.pendencias[0].dia").value(LocalDate.now(PendenciasService.FUSO).toString()))
                // A pasta (que mostra o usuário do Windows) não vaza na resposta
                .andExpect(content().string(not(containsString(PASTA.getFileName().toString()))));

        // Log: quem, o quê, quantos. Sem o texto das tarefas e sem o caminho.
        assertTrue(saida.getOut().contains("cliente=python acao=pendencias resultado=ok total=1 ignorados=0"));
        assertFalse(saida.getOut().contains("tarefa secreta"), "texto da tarefa não vai para o log");
        assertFalse(saida.getOut().contains(PASTA.toString()), "caminho não vai para o log");
    }

    @Test
    void semToken_401_mesmoSendoGet() throws Exception {
        mockMvc.perform(get("/acoes/pendencias"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string(not(containsString("tarefa secreta"))));
    }

    @Test
    void tokenErrado_401() throws Exception {
        mockMvc.perform(get("/acoes/pendencias").header(TokenInterceptor.CABECALHO, "token-errado"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void headSemToken_401() throws Exception {
        mockMvc.perform(head("/acoes/pendencias")).andExpect(status().isUnauthorized());
    }

    @Test
    void variacaoDaRotaPublicaNaoFuraOPorteiro() throws Exception {
        // Truques de URL: nenhum pode abrir a leitura sem token
        mockMvc.perform(get("/acoes/pendencias/")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/acoes//pendencias")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/acoes/hora/../pendencias")).andExpect(status().isUnauthorized());
    }
}
