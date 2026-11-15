package io.github.jjoseffelipe2028.bracos.central;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import io.github.jjoseffelipe2028.bracos.acoes.git.GitStatusService;
import io.github.jjoseffelipe2028.bracos.acoes.pendencias.PendenciasService;
import io.github.jjoseffelipe2028.bracos.acoes.ronda.RondaService;
import io.github.jjoseffelipe2028.bracos.erros.ErrosHandler;
import io.github.jjoseffelipe2028.bracos.erros.LimiteAtingidoException;
import io.github.jjoseffelipe2028.bracos.seguranca.WebConfig;
import jakarta.servlet.http.Cookie;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Ticket 061: botão 🛡️ Ronda do defensor na Central. Caminho feliz e ataques. 🔒 */
@WebMvcTest({CentralRondaController.class, CentralController.class})
@Import({ChaveDaCentral.class, SessoesDaCentral.class, QuadroDeEventos.class, WebConfig.class, ErrosHandler.class})
@ExtendWith(OutputCaptureExtension.class)
class CentralRondaControllerTest {

    private static final String ORIGEM = "http://localhost:80";
    private static final String ROTA = "/central/acoes/ronda";
    private static final Path ARQUIVO;

    static {
        try {
            ARQUIVO = Files.createTempDirectory("central-ronda-teste").resolve(".central-chave");
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void propriedades(DynamicPropertyRegistry registro) {
        registro.add("jarvis.token", () -> "token-de-teste"); // valor FICTÍCIO
        registro.add("jarvis.central.arquivo-chave", ARQUIVO::toString);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private QuadroDeEventos quadro;

    @MockitoBean
    private RondaService ronda;

    @MockitoBean
    private PendenciasService pendencias; // só para o CentralController subir

    @MockitoBean
    private GitStatusService git; // só para o CentralController subir

    private Cookie entrar() throws Exception {
        return mockMvc.perform(post("/central/sessao")
                        .header(CentralInterceptor.CABECALHO, "1").header("Origin", ORIGEM)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"chave\":\"" + Files.readString(ARQUIVO) + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getCookie(CentralInterceptor.COOKIE);
    }

    private String ultimoResultado() {
        List<QuadroDeEventos.Evento> eventos = quadro.depois(0).eventos();
        QuadroDeEventos.Evento e = eventos.get(eventos.size() - 1);
        assertEquals("ronda", e.acao());
        return e.resultado();
    }

    @Test
    void caminhoFeliz_mostraOsVigias_logSemFrases(CapturedOutput saida) throws Exception {
        when(ronda.fazerRonda()).thenReturn(new RondaService.Resultado("atencao", "Defensor: atenção em Rede.",
                List.of(new RondaService.Vigia("Rede", "atencao", "1 aparelho novo segredo-da-rede"))));
        mockMvc.perform(post(ROTA).cookie(entrar()).header(CentralInterceptor.CABECALHO, "1").header("Origin", ORIGEM)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.geral").value("atencao"))
                .andExpect(jsonPath("$.vigias[0].vigia").value("Rede"));
        assertTrue(saida.getOut().contains("cliente=central acao=ronda resultado=ok geral=atencao"));
        assertFalse(saida.getOut().contains("segredo-da-rede"), "as frases dos vigias não vão para o log");
        assertEquals("ok", ultimoResultado());
    }

    @Test
    void semCracha_401_semOrigin_403_siteDeFora_403_eGet405() throws Exception {
        mockMvc.perform(post(ROTA).header(CentralInterceptor.CABECALHO, "1").header("Origin", ORIGEM))
                .andExpect(status().isUnauthorized());
        Cookie cracha = entrar();
        mockMvc.perform(post(ROTA).cookie(cracha).header(CentralInterceptor.CABECALHO, "1"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(ROTA).cookie(cracha).header(CentralInterceptor.CABECALHO, "1")
                        .header("Origin", "https://site-malicioso.example"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(ROTA).cookie(cracha).header(CentralInterceptor.CABECALHO, "1"))
                .andExpect(status().isMethodNotAllowed());
        verify(ronda, never()).fazerRonda();
    }

    @Test
    void rondaJaRodando_429() throws Exception {
        when(ronda.fazerRonda()).thenThrow(new LimiteAtingidoException("ja tem uma ronda rodando"));
        mockMvc.perform(post(ROTA).cookie(entrar()).header(CentralInterceptor.CABECALHO, "1").header("Origin", ORIGEM))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.erro").value("ja tem uma ronda rodando"));
    }
}
