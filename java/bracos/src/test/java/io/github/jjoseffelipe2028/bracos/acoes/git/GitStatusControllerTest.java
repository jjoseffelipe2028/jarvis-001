package io.github.jjoseffelipe2028.bracos.acoes.git;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.jjoseffelipe2028.bracos.auditoria.Cliente;
import io.github.jjoseffelipe2028.bracos.erros.AcaoIndisponivelException;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** GET /acoes/git-status: porteiro, formato da resposta e log. O git em si é testado no Service. */
@WebMvcTest(GitStatusController.class)
@Import({WebConfig.class, ErrosHandler.class})
@TestPropertySource(properties = "jarvis.token=token-de-teste") // valor FICTÍCIO
@ExtendWith(OutputCaptureExtension.class)
class GitStatusControllerTest {

    private static final String TOKEN = "token-de-teste";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GitStatusService service;

    @Test
    void comToken_200_soNumeros(CapturedOutput saida) throws Exception {
        when(service.consultar()).thenReturn(new GitStatusService.Status("main", false, 2, 1, 0, 0));

        mockMvc.perform(get("/acoes/git-status")
                        .header(TokenInterceptor.CABECALHO, TOKEN)
                        .header(Cliente.CABECALHO, "python"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.branch").value("main"))
                .andExpect(jsonPath("$.limpo").value(false))
                .andExpect(jsonPath("$.alterados").value(2))
                .andExpect(jsonPath("$.novos").value(1));

        assertTrue(saida.getOut().contains("cliente=python acao=git-status resultado=ok limpo=false alterados=2 novos=1"));
    }

    @Test
    void semToken_401_eNemRodaOGit() throws Exception {
        mockMvc.perform(get("/acoes/git-status")).andExpect(status().isUnauthorized());
        org.mockito.Mockito.verifyNoInteractions(service);
    }

    @Test
    void gitIndisponivel_503ComMensagemCurta(CapturedOutput saida) throws Exception {
        when(service.consultar()).thenThrow(new AcaoIndisponivelException("repositorio indisponivel"));

        mockMvc.perform(get("/acoes/git-status").header(TokenInterceptor.CABECALHO, TOKEN))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.erro").value("repositorio indisponivel"));

        assertTrue(saida.getOut().contains("acao=git-status resultado=erro motivo=AcaoIndisponivelException"));
        assertFalse(saida.getOut().contains("motivo=repositorio"), "o log usa o tipo do erro, não a mensagem");
    }
}
