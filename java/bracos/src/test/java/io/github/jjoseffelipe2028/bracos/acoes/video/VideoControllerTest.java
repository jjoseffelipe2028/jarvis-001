package io.github.jjoseffelipe2028.bracos.acoes.video;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** POST /acoes/video/avaliar de ponta a ponta (porteiros + validação + regra). */
@WebMvcTest(VideoController.class)
@Import({PortaoDoLaboratorio.class, AnalisadorDeRetencao.class, RelatorioService.class,
        WebConfig.class, ErrosHandler.class})
@TestPropertySource(properties = {"jarvis.token=token-de-teste", // valor FICTÍCIO
        "jarvis.pastas.decisoes="})            // relatório: pasta configurada só no teste próprio
@ExtendWith(OutputCaptureExtension.class)
class VideoControllerTest {

    private static final String TOKEN = "token-de-teste";
    private static final String ROTA = "/acoes/video/avaliar";

    @Autowired
    private MockMvc mockMvc;

    private static String json(Object duracao, Object proporcao, Object media) {
        return "{\"duracaoSeg\":" + duracao + ",\"proporcao3sPara1min\":" + proporcao
                + ",\"mediaAssistidaSeg\":" + media + "}";
    }

    @Test
    void videoBom_libera_eLogNaoTemNumeros(CapturedOutput saida) throws Exception {
        mockMvc.perform(post(ROTA)
                        .header(TokenInterceptor.CABECALHO, TOKEN)
                        .header(Cliente.CABECALHO, "python")
                        .contentType(MediaType.APPLICATION_JSON).content(json(190, 55.5, 80)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.libera").value(true))
                .andExpect(jsonPath("$.motivos.length()").value(2));

        assertTrue(saida.getOut().contains("cliente=python acao=video_avaliar resultado=libera"));
    }

    @Test
    void videoFraco_bloqueia() throws Exception {
        mockMvc.perform(post(ROTA)
                        .header(TokenInterceptor.CABECALHO, TOKEN)
                        .contentType(MediaType.APPLICATION_JSON).content(json(190, 30, 40)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.libera").value(false));
    }

    @Test
    void semToken_401() throws Exception {
        mockMvc.perform(post(ROTA)
                        .contentType(MediaType.APPLICATION_JSON).content(json(190, 55, 80)))
                .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"duracaoSeg\":-5,\"proporcao3sPara1min\":55,\"mediaAssistidaSeg\":80}",   // duração negativa
            "{\"duracaoSeg\":190,\"proporcao3sPara1min\":150,\"mediaAssistidaSeg\":80}", // mais de 100%
            "{\"duracaoSeg\":190,\"proporcao3sPara1min\":-1,\"mediaAssistidaSeg\":80}",  // % negativa
            "{\"duracaoSeg\":100,\"proporcao3sPara1min\":55,\"mediaAssistidaSeg\":101}", // média > duração
            "{\"duracaoSeg\":190,\"mediaAssistidaSeg\":80}",                             // faltou campo
            "{\"duracaoSeg\":\"abc\",\"proporcao3sPara1min\":55,\"mediaAssistidaSeg\":80}", // texto no lugar de número
            "{\"duracaoSeg\":190,\"proporcao3sPara1min\":NaN,\"mediaAssistidaSeg\":80}", // NaN
            "{\"duracaoSeg\":999999,\"proporcao3sPara1min\":55,\"mediaAssistidaSeg\":80}", // mais de 10 h
            "nao e json"
    })
    void entradaRuim_400(String corpo) throws Exception {
        mockMvc.perform(post(ROTA)
                        .header(TokenInterceptor.CABECALHO, TOKEN)
                        .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("entrada invalida"));
    }

    @Test
    void relatorioSemPastaConfigurada_503() throws Exception {
        String corpo = "{\"titulo\":\"x\",\"plataforma\":\"facebook\",\"duracaoSeg\":190,"
                + "\"proporcao3sPara1min\":55,\"mediaAssistidaSeg\":80,\"ctrPct\":4}";
        mockMvc.perform(post("/acoes/video/relatorio")
                        .header(TokenInterceptor.CABECALHO, TOKEN)
                        .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isServiceUnavailable());
    }
}
