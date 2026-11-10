package io.github.jjoseffelipe2028.bracos.acoes.sentinela;

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

import java.util.List;

import io.github.jjoseffelipe2028.bracos.auditoria.Cliente;
import io.github.jjoseffelipe2028.bracos.central.QuadroDeEventos;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** GET /acoes/sentinela/portas: protegido pelo token, só leitura, vira evento no escritório. 🛡️ */
@WebMvcTest(SentinelaController.class)
@Import({QuadroDeEventos.class, DetectorDeGolpe.class, WebConfig.class, ErrosHandler.class})
@TestPropertySource(properties = "jarvis.token=token-de-teste") // valor FICTÍCIO
@ExtendWith(OutputCaptureExtension.class)
class SentinelaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private QuadroDeEventos quadro;

    @MockitoBean
    private PortasService portas;

    @MockitoBean
    private IntegridadeService integridade;

    private static final PortasService.Relatorio EXEMPLO = new PortasService.Relatorio(5, 2, 3, 0, true, List.of(
            new PortasService.PortaAberta(445, "toda a rede", "atencao", "SMB: compartilhamento de arquivos")));

    @Test
    void comToken_200_eLogSoComNumeros(CapturedOutput saida) throws Exception {
        when(portas.vigiar()).thenReturn(EXEMPLO);
        mockMvc.perform(get("/acoes/sentinela/portas")
                        .header(TokenInterceptor.CABECALHO, "token-de-teste").header(Cliente.CABECALHO, "python"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.abertasParaARede").value(3))
                .andExpect(jsonPath("$.jarvisSoNoPc").value(true))
                .andExpect(jsonPath("$.portas[0].porta").value(445));
        assertTrue(saida.getOut().contains("cliente=python acao=sentinela resultado=ok rede=3 alertas=0"));
        List<QuadroDeEventos.Evento> eventos = quadro.depois(0).eventos();
        assertEquals("sentinela", eventos.get(eventos.size() - 1).acao(), "o escritório fica sabendo");
    }

    @Test
    void semToken_401_eNemRodaONetstat() throws Exception {
        mockMvc.perform(get("/acoes/sentinela/portas")).andExpect(status().isUnauthorized());
        verify(portas, never()).vigiar();
    }

    @Test
    void integridade_comToken_200_eViraEvento() throws Exception {
        when(integridade.conferir()).thenReturn(new IntegridadeService.Relatorio(5, 1, "02:10", List.of(
                new IntegridadeService.Arquivo(".claude/hooks/guarda.mjs", "alterado", "a1b2c3d4e5f6"))));
        mockMvc.perform(get("/acoes/sentinela/integridade").header(TokenInterceptor.CABECALHO, "token-de-teste"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alterados").value(1))
                .andExpect(jsonPath("$.arquivos[0].estado").value("alterado"));
        List<QuadroDeEventos.Evento> eventos = quadro.depois(0).eventos();
        assertEquals("integridade", eventos.get(eventos.size() - 1).acao());
    }

    @Test
    void integridade_semToken_401() throws Exception {
        mockMvc.perform(get("/acoes/sentinela/integridade")).andExpect(status().isUnauthorized());
        verify(integridade, never()).conferir();
    }

    @Test
    void golpe_comToken_200_naoLogaOTexto_eViraEvento(CapturedOutput saida) throws Exception {
        mockMvc.perform(post("/acoes/sentinela/golpe")
                        .header(TokenInterceptor.CABECALHO, "token-de-teste").header(Cliente.CABECALHO, "python")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"texto\":\"Oi mae, troquei de numero, faz um PIX urgente\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nivel").value("alto"))
                .andExpect(jsonPath("$.conselho").exists());
        assertTrue(saida.getOut().contains("cliente=python acao=golpe resultado=ok nivel=alto links=0"));
        assertFalse(saida.getOut().contains("troquei de numero"), "o texto colado não vai para o log");
        List<QuadroDeEventos.Evento> eventos = quadro.depois(0).eventos();
        assertEquals("golpe", eventos.get(eventos.size() - 1).acao());
    }

    @Test
    void golpe_semToken_401_vazio_ouGrande_400() throws Exception {
        mockMvc.perform(post("/acoes/sentinela/golpe").contentType(MediaType.APPLICATION_JSON)
                .content("{\"texto\":\"oi\"}")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/acoes/sentinela/golpe").header(TokenInterceptor.CABECALHO, "token-de-teste")
                .contentType(MediaType.APPLICATION_JSON).content("{\"texto\":\"  \"}")).andExpect(status().isBadRequest());
        mockMvc.perform(post("/acoes/sentinela/golpe").header(TokenInterceptor.CABECALHO, "token-de-teste")
                .contentType(MediaType.APPLICATION_JSON).content("{\"texto\":\"" + "a".repeat(4001) + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void foraDoWindows_503() throws Exception {
        when(portas.vigiar()).thenThrow(new AcaoIndisponivelException("sentinela so no windows"));
        mockMvc.perform(get("/acoes/sentinela/portas").header(TokenInterceptor.CABECALHO, "token-de-teste"))
                .andExpect(status().isServiceUnavailable());
    }
}
