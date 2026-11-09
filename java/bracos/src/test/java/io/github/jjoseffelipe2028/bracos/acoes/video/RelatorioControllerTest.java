package io.github.jjoseffelipe2028.bracos.acoes.video;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import io.github.jjoseffelipe2028.bracos.auditoria.Cliente;
import io.github.jjoseffelipe2028.bracos.erros.ErrosHandler;
import io.github.jjoseffelipe2028.bracos.seguranca.TokenInterceptor;
import io.github.jjoseffelipe2028.bracos.seguranca.WebConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** POST /acoes/video/relatorio de ponta a ponta, gravando numa pasta temporária. */
@WebMvcTest(VideoController.class)
@Import({PortaoDoLaboratorio.class, AnalisadorDeRetencao.class, RelatorioService.class,
        WebConfig.class, ErrosHandler.class})
@ExtendWith(OutputCaptureExtension.class)
class RelatorioControllerTest {

    private static final String TOKEN = "token-de-teste"; // valor FICTÍCIO
    private static final String ROTA = "/acoes/video/relatorio";
    private static final Path PASTA;

    static {
        try {
            PASTA = Files.createTempDirectory("decisoes-teste");
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void propriedades(DynamicPropertyRegistry registro) {
        registro.add("jarvis.token", () -> TOKEN);
        registro.add("jarvis.pastas.decisoes", PASTA::toString);
    }

    @Autowired
    private MockMvc mockMvc;

    private static final String BOM = "{\"titulo\":\"titulo secreto\",\"plataforma\":\"facebook\",\"duracaoSeg\":190,"
            + "\"proporcao3sPara1min\":55,\"mediaAssistidaSeg\":80,\"ctrPct\":4,\"quedasSeg\":[2,55]}";

    @Test
    void caminhoFeliz_gravaNota_eNaoVazaCaminhoNemTitulo(CapturedOutput saida) throws Exception {
        mockMvc.perform(post(ROTA)
                        .header(TokenInterceptor.CABECALHO, TOKEN)
                        .header(Cliente.CABECALHO, "python")
                        .contentType(MediaType.APPLICATION_JSON).content(BOM))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.arquivo").value(org.hamcrest.Matchers.matchesPattern(
                        "\\d{4}-\\d{2}-\\d{2}-\\d{6}(-\\d+)?-retencao\\.md")))
                .andExpect(jsonPath("$.previsao").value("promissor, com ajustes"))
                .andExpect(jsonPath("$.dublagem.libera").value(true))
                .andExpect(jsonPath("$.achados.length()").value(2))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString(PASTA.getFileName().toString()))));

        try (var arquivos = Files.list(PASTA)) {
            assertTrue(arquivos.findAny().isPresent(), "a nota foi criada");
        }
        assertTrue(saida.getOut().contains("cliente=python acao=video_relatorio resultado=ok"));
        assertFalse(saida.getOut().contains("titulo secreto"), "título não vai para o log");
        assertFalse(saida.getOut().contains(PASTA.toString()), "caminho não vai para o log");
    }

    @Test
    void semToken_401() throws Exception {
        mockMvc.perform(post(ROTA).contentType(MediaType.APPLICATION_JSON).content(BOM))
                .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            // plataforma fora da lista
            "{\"titulo\":\"x\",\"plataforma\":\"../etc\",\"duracaoSeg\":190,\"proporcao3sPara1min\":55,\"mediaAssistidaSeg\":80,\"ctrPct\":4}",
            // título vazio
            "{\"titulo\":\"  \",\"plataforma\":\"youtube\",\"duracaoSeg\":190,\"proporcao3sPara1min\":55,\"mediaAssistidaSeg\":80,\"ctrPct\":4}",
            // CTR acima de 100
            "{\"titulo\":\"x\",\"plataforma\":\"youtube\",\"duracaoSeg\":190,\"proporcao3sPara1min\":55,\"mediaAssistidaSeg\":80,\"ctrPct\":101}",
            // queda depois do fim do vídeo
            "{\"titulo\":\"x\",\"plataforma\":\"youtube\",\"duracaoSeg\":100,\"proporcao3sPara1min\":55,\"mediaAssistidaSeg\":80,\"ctrPct\":4,\"quedasSeg\":[101]}",
            // queda negativa
            "{\"titulo\":\"x\",\"plataforma\":\"youtube\",\"duracaoSeg\":190,\"proporcao3sPara1min\":55,\"mediaAssistidaSeg\":80,\"ctrPct\":4,\"quedasSeg\":[-1]}",
            // queda nula
            "{\"titulo\":\"x\",\"plataforma\":\"youtube\",\"duracaoSeg\":190,\"proporcao3sPara1min\":55,\"mediaAssistidaSeg\":80,\"ctrPct\":4,\"quedasSeg\":[null]}",
            // mais de 20 quedas
            "{\"titulo\":\"x\",\"plataforma\":\"youtube\",\"duracaoSeg\":190,\"proporcao3sPara1min\":55,\"mediaAssistidaSeg\":80,\"ctrPct\":4,\"quedasSeg\":[1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21]}"
    })
    void entradaRuim_400(String corpo) throws Exception {
        mockMvc.perform(post(ROTA)
                        .header(TokenInterceptor.CABECALHO, TOKEN)
                        .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isBadRequest());
    }

    @Test
    void tituloGigante_400() throws Exception {
        String corpo = BOM.replace("titulo secreto", "a".repeat(121));
        mockMvc.perform(post(ROTA)
                        .header(TokenInterceptor.CABECALHO, TOKEN)
                        .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isBadRequest());
    }
}
