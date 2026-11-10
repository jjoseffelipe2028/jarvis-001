package io.github.jjoseffelipe2028.bracos.central;

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
import io.github.jjoseffelipe2028.bracos.acoes.sentinela.DetectorDeGolpe;
import io.github.jjoseffelipe2028.bracos.acoes.sentinela.IntegridadeService;
import io.github.jjoseffelipe2028.bracos.acoes.sentinela.PortasService;
import io.github.jjoseffelipe2028.bracos.erros.ErrosHandler;
import io.github.jjoseffelipe2028.bracos.seguranca.WebConfig;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Ticket 021: a Sentinela pelo botão da Central, atrás do crachá. 🛡️ */
@WebMvcTest({CentralSentinelaController.class, CentralController.class})
@Import({ChaveDaCentral.class, SessoesDaCentral.class, QuadroDeEventos.class, DetectorDeGolpe.class, WebConfig.class,
        ErrosHandler.class})
class CentralSentinelaControllerTest {

    private static final Path ARQUIVO;

    static {
        try {
            ARQUIVO = Files.createTempDirectory("central-sentinela-teste").resolve(".central-chave");
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

    @MockitoBean
    private PortasService portas;

    @MockitoBean
    private IntegridadeService integridade;

    @MockitoBean
    private PendenciasService pendencias; // só para o CentralController subir

    @MockitoBean
    private GitStatusService git; // só para o CentralController subir

    private Cookie entrar() throws Exception {
        return mockMvc.perform(post("/central/sessao").header(CentralInterceptor.CABECALHO, "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"chave\":\"" + Files.readString(ARQUIVO) + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getCookie(CentralInterceptor.COOKIE);
    }

    @Test
    void comCracha_200() throws Exception {
        when(portas.vigiar()).thenReturn(new PortasService.Relatorio(3, 1, 2, 0, true, List.of()));
        mockMvc.perform(get("/central/acoes/sentinela/portas").cookie(entrar()).header(CentralInterceptor.CABECALHO, "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.abertasParaARede").value(2));
    }

    @Test
    void integridade_comCracha_200_semCracha_401() throws Exception {
        when(integridade.conferir()).thenReturn(new IntegridadeService.Relatorio(5, 0, "02:10", List.of()));
        mockMvc.perform(get("/central/acoes/sentinela/integridade").cookie(entrar()).header(CentralInterceptor.CABECALHO, "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vigiados").value(5));
        mockMvc.perform(get("/central/acoes/sentinela/integridade").header(CentralInterceptor.CABECALHO, "1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void semCracha_401_eNemRoda() throws Exception {
        mockMvc.perform(get("/central/acoes/sentinela/portas").header(CentralInterceptor.CABECALHO, "1"))
                .andExpect(status().isUnauthorized());
        verify(portas, never()).vigiar();
    }

    private static final String ORIGEM = "http://localhost:80"; // a própria Central no MockMvc
    private static final String GOLPE = "{\"texto\":\"NUBANK: conta bloqueada, regularize em http://nubank-seguro.xyz\"}";

    @Autowired
    private QuadroDeEventos quadro;

    @Test
    void golpe_comCrachaEOrigin_200_eViraEvento() throws Exception {
        mockMvc.perform(post("/central/acoes/sentinela/golpe").cookie(entrar()).header(CentralInterceptor.CABECALHO, "1")
                        .header("Origin", ORIGEM).contentType(MediaType.APPLICATION_JSON).content(GOLPE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nivel").value("alto"))
                .andExpect(jsonPath("$.links").value(1));
        List<QuadroDeEventos.Evento> eventos = quadro.depois(0).eventos();
        org.junit.jupiter.api.Assertions.assertEquals("central", eventos.get(eventos.size() - 1).cliente());
    }

    @Test
    void golpe_semOrigin_403_semCracha_401_siteDeFora_403() throws Exception {
        Cookie cracha = entrar();
        mockMvc.perform(post("/central/acoes/sentinela/golpe").cookie(cracha).header(CentralInterceptor.CABECALHO, "1")
                        .contentType(MediaType.APPLICATION_JSON).content(GOLPE))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/central/acoes/sentinela/golpe").header(CentralInterceptor.CABECALHO, "1")
                        .header("Origin", ORIGEM).contentType(MediaType.APPLICATION_JSON).content(GOLPE))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/central/acoes/sentinela/golpe").cookie(cracha).header(CentralInterceptor.CABECALHO, "1")
                        .header("Origin", "https://site-malicioso.example").contentType(MediaType.APPLICATION_JSON).content(GOLPE))
                .andExpect(status().isForbidden());
    }

    @Test
    void siteDeFora_403() throws Exception {
        mockMvc.perform(get("/central/acoes/sentinela/portas").cookie(entrar()).header(CentralInterceptor.CABECALHO, "1")
                        .header("Sec-Fetch-Site", "cross-site"))
                .andExpect(status().isForbidden());
        verify(portas, never()).vigiar();
    }
}
