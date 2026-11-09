package io.github.jjoseffelipe2028.bracos.seguranca;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.concurrent.ThreadLocalRandom;

import io.github.jjoseffelipe2028.bracos.acoes.daily.DailyController;
import io.github.jjoseffelipe2028.bracos.acoes.daily.DailyService;
import io.github.jjoseffelipe2028.bracos.erros.ErrosHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** Ticket 007 de ponta a ponta: POST /acoes/daily ASSINADO, sem mandar o token. */
@WebMvcTest(DailyController.class)
@Import({DailyService.class, WebConfig.class, ErrosHandler.class, CorpoGuardadoFilter.class})
class AssinaturaDePontaAPontaTest {

    private static final String TOKEN = "token-de-teste"; // valor FICTÍCIO
    private static final Path PASTA;

    static {
        try {
            PASTA = Files.createTempDirectory("assinatura-teste");
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

    private static String nonce() {
        byte[] b = new byte[16];
        ThreadLocalRandom.current().nextBytes(b);
        return HexFormat.of().formatHex(b);
    }

    /** Monta o pedido assinado como o Python faz. */
    private static MockHttpServletRequestBuilder assinado(String corpoAssinado, String corpoEnviado, String nonce) {
        String hora = String.valueOf(System.currentTimeMillis() / 1000);
        byte[] sub = ProvaController.hmacBytes(TOKEN.getBytes(StandardCharsets.UTF_8), Assinatura.ROTULO_SUBCHAVE);
        String ass = Assinatura.assinar(sub, "POST", "/acoes/daily", hora, nonce,
                corpoAssinado.getBytes(StandardCharsets.UTF_8));
        return MockMvcRequestBuilders.post("/acoes/daily").contentType(MediaType.APPLICATION_JSON)
                .content(corpoEnviado)
                .header(Assinatura.CAB_HORA, hora).header(Assinatura.CAB_NONCE, nonce)
                .header(Assinatura.CAB_ASSINATURA, ass);
    }

    private String notaDeHoje() throws IOException {
        Path p = PASTA.resolve(LocalDate.now(java.time.ZoneId.of("America/Sao_Paulo")) + ".md");
        return Files.exists(p) ? Files.readString(p, StandardCharsets.UTF_8) : "";
    }

    @Test
    void pedidoAssinado_semToken_grava() throws Exception {
        String corpo = "{\"texto\":\"assinado sem token\"}";
        int status = mockMvc.perform(assinado(corpo, corpo, nonce())).andReturn().getResponse().getStatus();
        assertEquals(200, status);
        assertTrue(notaDeHoje().contains("assinado sem token"));
    }

    @Test
    void corpoTrocadoNoCaminho_401_eNaoGrava() throws Exception {
        int status = mockMvc.perform(assinado("{\"texto\":\"original\"}", "{\"texto\":\"trocado pelo atacante\"}", nonce()))
                .andReturn().getResponse().getStatus();
        assertEquals(401, status);
        assertFalse(notaDeHoje().contains("trocado pelo atacante"));
    }

    @Test
    void repetirOMesmoPedido_oSegundoE401() throws Exception {
        String corpo = "{\"texto\":\"so uma vez\"}";
        String n = nonce();
        assertEquals(200, mockMvc.perform(assinado(corpo, corpo, n)).andReturn().getResponse().getStatus());
        assertEquals(401, mockMvc.perform(assinado(corpo, corpo, n)).andReturn().getResponse().getStatus());
        assertEquals(1, notaDeHoje().split("so uma vez", -1).length - 1, "gravou duas vezes");
    }

    @Test
    void corpoGigante_413() throws Exception {
        String gigante = "{\"texto\":\"" + "a".repeat(CorpoGuardadoFilter.LIMITE_CORPO) + "\"}";
        int status = mockMvc.perform(MockMvcRequestBuilders.post("/acoes/daily")
                .contentType(MediaType.APPLICATION_JSON).content(gigante)).andReturn().getResponse().getStatus();
        assertEquals(413, status);
    }

    @Test
    void tokenNoCabecalho_continuaValendoParaCurl() throws Exception {
        int status = mockMvc.perform(MockMvcRequestBuilders.post("/acoes/daily")
                .header(TokenInterceptor.CABECALHO, TOKEN)
                .contentType(MediaType.APPLICATION_JSON).content("{\"texto\":\"via curl\"}"))
                .andReturn().getResponse().getStatus();
        assertEquals(200, status);
    }
}
