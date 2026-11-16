package io.github.jjoseffelipe2028.bracos.central;

import static org.junit.jupiter.api.Assertions.assertFalse;
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

import io.github.jjoseffelipe2028.bracos.acoes.dupla.DuplaService;
import io.github.jjoseffelipe2028.bracos.acoes.git.GitStatusService;
import io.github.jjoseffelipe2028.bracos.acoes.pendencias.PendenciasService;
import io.github.jjoseffelipe2028.bracos.erros.AcaoIndisponivelException;
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

/** Ticket 072 (parte C): GET /central/acoes/dupla. Só data, título e veredito; atrás do crachá. 🤝 */
@WebMvcTest({CentralDuplaController.class, CentralController.class})
@Import({ChaveDaCentral.class, SessoesDaCentral.class, QuadroDeEventos.class, WebConfig.class, ErrosHandler.class})
class CentralDuplaControllerTest {

    private static final String ORIGEM = "http://localhost:80";
    private static final Path ARQUIVO;

    static {
        try {
            ARQUIVO = Files.createTempDirectory("central-dupla-teste").resolve(".central-chave");
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
    private DuplaService dupla;

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

    @Test
    void comCracha_200_soDataTituloEVeredito() throws Exception {
        when(dupla.ultimos()).thenReturn(List.of(
                new DuplaService.Registro("2026-11-15", "vale usar records?", DuplaService.Veredito.RESPONDIDO)));
        String corpo = mockMvc.perform(get("/central/acoes/dupla").cookie(entrar()).header(CentralInterceptor.CABECALHO, "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.registros[0].data").value("2026-11-15"))
                .andExpect(jsonPath("$.registros[0].veredito").value("RESPONDIDO"))
                .andReturn().getResponse().getContentAsString();
        assertFalse(corpo.contains("texto"), "a resposta não tem campo de texto da nota");
    }

    @Test
    void semCracha_401_eNemLeAPasta() throws Exception {
        mockMvc.perform(get("/central/acoes/dupla").header(CentralInterceptor.CABECALHO, "1"))
                .andExpect(status().isUnauthorized());
        verify(dupla, never()).ultimos();
    }

    @Test
    void pastaNaoConfigurada_503() throws Exception {
        when(dupla.ultimos()).thenThrow(new AcaoIndisponivelException("pasta nao configurada"));
        mockMvc.perform(get("/central/acoes/dupla").cookie(entrar()).header(CentralInterceptor.CABECALHO, "1"))
                .andExpect(status().isServiceUnavailable());
    }
}
