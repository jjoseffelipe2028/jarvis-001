package io.github.jjoseffelipe2028.bracos.central;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import io.github.jjoseffelipe2028.bracos.acoes.git.GitStatusService;
import io.github.jjoseffelipe2028.bracos.acoes.pendencias.PendenciasService;
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
import org.springframework.test.web.servlet.MvcResult;

/** Central v1 de ponta a ponta: chave → crachá → botões. Com ataques. 🔒 */
@WebMvcTest(CentralController.class)
@Import({ChaveDaCentral.class, SessoesDaCentral.class, QuadroDeEventos.class, WebConfig.class, ErrosHandler.class})
class CentralControllerTest {

    private static final Path ARQUIVO;

    static {
        try {
            ARQUIVO = Files.createTempDirectory("central-teste").resolve(".central-chave");
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
    private PendenciasService pendencias;

    @MockitoBean
    private GitStatusService git;

    @Autowired
    private QuadroDeEventos quadro;

    private static String chaveAtual() throws IOException {
        return Files.readString(ARQUIVO);
    }

    private static String corpo(String chave) {
        return "{\"chave\":\"" + chave + "\"}";
    }

    /** Faz o caminho certo e devolve o crachá. */
    private Cookie entrar() throws Exception {
        MvcResult r = mockMvc.perform(post("/central/sessao")
                        .header(CentralInterceptor.CABECALHO, "1")
                        .header("Origin", "http://localhost:80")
                        .contentType(MediaType.APPLICATION_JSON).content(corpo(chaveAtual())))
                .andExpect(status().isOk())
                .andReturn();
        return r.getResponse().getCookie(CentralInterceptor.COOKIE);
    }

    @Test
    void chaveCerta_daCrachaHttpOnlyStrict() throws Exception {
        mockMvc.perform(post("/central/sessao")
                        .header(CentralInterceptor.CABECALHO, "1")
                        .contentType(MediaType.APPLICATION_JSON).content(corpo(chaveAtual())))
                .andExpect(status().isOk())
                .andExpect(header().string("Set-Cookie", allOf(
                        containsString(CentralInterceptor.COOKIE + "="),
                        containsString("HttpOnly"),
                        containsString("SameSite=Strict"),
                        containsString("Path=/central"))));
    }

    @Test
    void mesmaChaveDuasVezes_segundaRecusada() throws Exception {
        String chave = chaveAtual();
        mockMvc.perform(post("/central/sessao").header(CentralInterceptor.CABECALHO, "1")
                        .contentType(MediaType.APPLICATION_JSON).content(corpo(chave)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/central/sessao").header(CentralInterceptor.CABECALHO, "1")
                        .contentType(MediaType.APPLICATION_JSON).content(corpo(chave)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void chaveErradaOuSemCorpo_401() throws Exception {
        mockMvc.perform(post("/central/sessao").header(CentralInterceptor.CABECALHO, "1")
                        .contentType(MediaType.APPLICATION_JSON).content(corpo("0".repeat(64))))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/central/sessao").header(CentralInterceptor.CABECALHO, "1")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/central/sessao").header(CentralInterceptor.CABECALHO, "1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void semCabecalhoDaCentral_401MesmoComChaveCerta() throws Exception {
        mockMvc.perform(post("/central/sessao")
                        .contentType(MediaType.APPLICATION_JSON).content(corpo(chaveAtual())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void siteDeFora_403() throws Exception {
        mockMvc.perform(post("/central/sessao").header(CentralInterceptor.CABECALHO, "1")
                        .header("Origin", "https://site-malicioso.example")
                        .contentType(MediaType.APPLICATION_JSON).content(corpo(chaveAtual())))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/central/sessao").header(CentralInterceptor.CABECALHO, "1")
                        .header("Sec-Fetch-Site", "cross-site")
                        .contentType(MediaType.APPLICATION_JSON).content(corpo(chaveAtual())))
                .andExpect(status().isForbidden());
    }

    @Test
    void hostFalso_dnsRebinding_403() throws Exception {
        mockMvc.perform(get("/central/acoes/git-status").header("Host", "evil.example")
                        .header(CentralInterceptor.CABECALHO, "1"))
                .andExpect(status().isForbidden());
    }

    @Test
    void botaoSemCracha_401_eNaoExecuta() throws Exception {
        mockMvc.perform(get("/central/acoes/pendencias").header(CentralInterceptor.CABECALHO, "1"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/central/acoes/pendencias").header(CentralInterceptor.CABECALHO, "1")
                        .cookie(new Cookie(CentralInterceptor.COOKIE, "0".repeat(64))))
                .andExpect(status().isUnauthorized());
        verify(pendencias, never()).listar();
    }

    @Test
    void botaoComCrachaMasSemCabecalho_401() throws Exception {
        Cookie cracha = entrar();
        mockMvc.perform(get("/central/acoes/git-status").cookie(cracha))
                .andExpect(status().isUnauthorized());
        verify(git, never()).consultar();
    }

    @Test
    void caminhoFeliz_pendenciasEGit() throws Exception {
        when(pendencias.listar()).thenReturn(new PendenciasService.Resultado(
                List.of(new PendenciasService.Pendencia("2026-11-09", "Assistir aula 02 de Cyber")), 1, 0));
        when(git.consultar()).thenReturn(new GitStatusService.Status("main", true, 0, 0, 0, 0));
        Cookie cracha = entrar();

        mockMvc.perform(get("/central/acoes/pendencias").header(CentralInterceptor.CABECALHO, "1").cookie(cracha))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.pendencias[0].texto").value("Assistir aula 02 de Cyber"));
        mockMvc.perform(get("/central/acoes/git-status").header(CentralInterceptor.CABECALHO, "1").cookie(cracha))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.limpo").value(true));
    }

    @Test
    void eventos_semCracha401_comCrachaDevolveSoOsNovos() throws Exception {
        mockMvc.perform(get("/central/acoes/eventos").header(CentralInterceptor.CABECALHO, "1"))
                .andExpect(status().isUnauthorized());
        Cookie cracha = entrar();
        long antes = quadro.depois(0).ultimo();
        quadro.registrar("daily", "ok", "python");

        mockMvc.perform(get("/central/acoes/eventos").param("depois", String.valueOf(antes))
                        .header(CentralInterceptor.CABECALHO, "1").cookie(cracha))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ultimo").value(antes + 1))
                .andExpect(jsonPath("$.eventos.length()").value(1))
                .andExpect(jsonPath("$.eventos[0].acao").value("daily"))
                .andExpect(jsonPath("$.eventos[0].cliente").value("python"));
    }

    @Test
    void eventos_numeroTorto_viraZero() {
        org.junit.jupiter.api.Assertions.assertEquals(0, CentralController.numero(null));
        org.junit.jupiter.api.Assertions.assertEquals(0, CentralController.numero("-5"));
        org.junit.jupiter.api.Assertions.assertEquals(0, CentralController.numero("1e9"));
        org.junit.jupiter.api.Assertions.assertEquals(0, CentralController.numero("9".repeat(40)));
        org.junit.jupiter.api.Assertions.assertEquals(42, CentralController.numero("42"));
    }

    @Test
    void sessaoComCorpoGigante_413_antesDeLerOJson() throws Exception {
        // Ticket 019 (achado 2): a Central também tem o limite de 64 KB
        String gigante = corpo("a".repeat(64 * 1024)); // = CorpoGuardadoFilter.LIMITE_CORPO
        mockMvc.perform(post("/central/sessao")
                        .header(CentralInterceptor.CABECALHO, "1")
                        .contentType(MediaType.APPLICATION_JSON).content(gigante))
                .andExpect(status().isPayloadTooLarge());
    }

    @Test
    void respostaRecusada_tambemSaiComCabecalhosDeSeguranca() throws Exception {
        // Ticket 019 (achado 1): até o 401 leva a trava contra iframe e o no-store
        mockMvc.perform(get("/central/acoes/pendencias").header(CentralInterceptor.CABECALHO, "1"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Content-Security-Policy", "frame-ancestors 'none'"))
                .andExpect(header().string("Cache-Control", "no-store"));
    }
}
