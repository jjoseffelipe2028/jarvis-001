package io.github.jjoseffelipe2028.bracos.seguranca;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import io.github.jjoseffelipe2028.bracos.seguranca.Assinatura.Resultado;
import org.junit.jupiter.api.Test;

/** Ticket 007: a conferência da assinatura, sem servidor. Token FICTÍCIO. */
class AssinaturaTest {

    static final String TOKEN = "token-de-teste";
    static final long AGORA = 1_794_201_600L;
    static final String NONCE = "00112233445566778899aabbccddeeff";
    static final byte[] CORPO = "{\"texto\":\"oi\"}".getBytes(StandardCharsets.UTF_8);
    /** Calculado pelo PYTHON (mesmo vetor em python/tests/test_bracos.py): os dois lados assinam igual. */
    static final String VETOR = "deb27f36b4e4bb61231729c72bad6a6f422d68b41d1b77e2ac440386023a7a7a";

    private static Assinatura nova(long agora) {
        return new Assinatura(TOKEN, Clock.fixed(Instant.ofEpochSecond(agora), ZoneOffset.UTC));
    }

    @Test
    void vetorDoPython_passa() {
        assertEquals(Resultado.OK, nova(AGORA).conferir("POST", "/acoes/daily", "" + AGORA, NONCE, CORPO, VETOR));
    }

    @Test
    void mesmoPedidoDuasVezes_eRecusado() {
        Assinatura a = nova(AGORA);
        assertEquals(Resultado.OK, a.conferir("POST", "/acoes/daily", "" + AGORA, NONCE, CORPO, VETOR));
        assertEquals(Resultado.REPETIDO, a.conferir("POST", "/acoes/daily", "" + AGORA, NONCE, CORPO, VETOR));
    }

    @Test
    void trocarQualquerPedaco_quebraAAssinatura() {
        Assinatura a = nova(AGORA);
        assertEquals(Resultado.ASSINATURA_ERRADA, a.conferir("PUT", "/acoes/daily", "" + AGORA, NONCE, CORPO, VETOR));
        assertEquals(Resultado.ASSINATURA_ERRADA, a.conferir("POST", "/acoes/outra", "" + AGORA, NONCE, CORPO, VETOR));
        assertEquals(Resultado.ASSINATURA_ERRADA, a.conferir("POST", "/acoes/daily", "" + AGORA, NONCE,
                "{\"texto\":\"apaga tudo\"}".getBytes(StandardCharsets.UTF_8), VETOR));
        assertEquals(Resultado.ASSINATURA_ERRADA, a.conferir("POST", "/acoes/daily", "" + (AGORA + 1), NONCE, CORPO, VETOR));
        assertEquals(Resultado.ASSINATURA_ERRADA, a.conferir("POST", "/acoes/daily", "" + AGORA,
                NONCE.replace('0', '9'), CORPO, VETOR));
    }

    @Test
    void outroTokenNaoAssinaIgual() {
        Assinatura outra = new Assinatura("outro-token", Clock.fixed(Instant.ofEpochSecond(AGORA), ZoneOffset.UTC));
        assertEquals(Resultado.ASSINATURA_ERRADA, outra.conferir("POST", "/acoes/daily", "" + AGORA, NONCE, CORPO, VETOR));
    }

    /** Relógio que dá para adiantar no teste. */
    static final class RelogioMovel extends Clock {
        long segundos;
        RelogioMovel(long s) { segundos = s; }
        @Override public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId z) { return this; }
        @Override public Instant instant() { return Instant.ofEpochSecond(segundos); }
    }

    @Test
    void horaVelhaOuDoFuturo_eRecusada() {
        RelogioMovel r = new RelogioMovel(AGORA - 200);
        Assinatura a = new Assinatura(TOKEN, r); // API ligou antes do pedido
        r.segundos = AGORA + 61;
        assertEquals(Resultado.HORA_VENCIDA, a.conferir("POST", "/acoes/daily", "" + AGORA, NONCE, CORPO, VETOR));
        r.segundos = AGORA - 61;
        assertEquals(Resultado.HORA_VENCIDA, a.conferir("POST", "/acoes/daily", "" + AGORA, NONCE, CORPO, VETOR));
        // Dentro da janela de 60 s: passa
        r.segundos = AGORA + 60;
        assertEquals(Resultado.OK, a.conferir("POST", "/acoes/daily", "" + AGORA, NONCE, CORPO, VETOR));
    }

    /** Achado 1 do hacker (007): a lista de nonces some no reinício; pedido de ANTES do início não vale. */
    @Test
    void pedidoAssinadoAntesDeAApiLigar_eRecusado() {
        Assinatura reiniciada = nova(AGORA + 5); // a API (re)ligou 5 s depois de o pedido ser assinado
        assertEquals(Resultado.HORA_VENCIDA,
                reiniciada.conferir("POST", "/acoes/daily", "" + AGORA, NONCE, CORPO, VETOR));
    }

    /** Achado 2 do hacker (007): sem o corpo guardado pelo filtro, a assinatura é recusada (falha fechada). */
    @Test
    void semCorpoGuardado_oPorteiroRecusa() throws Exception {
        TokenInterceptor porteiro = new TokenInterceptor(TOKEN, Clock.fixed(Instant.ofEpochSecond(AGORA), ZoneOffset.UTC));
        var pedido = new org.springframework.mock.web.MockHttpServletRequest("POST", "/acoes/daily");
        pedido.setContent(CORPO);
        pedido.addHeader(Assinatura.CAB_HORA, "" + AGORA);
        pedido.addHeader(Assinatura.CAB_NONCE, NONCE);
        pedido.addHeader(Assinatura.CAB_ASSINATURA, VETOR); // assinatura CERTA, mas o filtro não rodou
        var resposta = new org.springframework.mock.web.MockHttpServletResponse();
        org.junit.jupiter.api.Assertions.assertFalse(porteiro.preHandle(pedido, resposta, new Object()));
        assertEquals(401, resposta.getStatus());
    }

    @Test
    void formatoEstranho_eRecusadoAntesDeQualquerConta() {
        Assinatura a = nova(AGORA);
        assertEquals(Resultado.FORMATO, a.conferir("POST", "/acoes/daily", null, NONCE, CORPO, VETOR));
        assertEquals(Resultado.FORMATO, a.conferir("POST", "/acoes/daily", "12a", NONCE, CORPO, VETOR));
        assertEquals(Resultado.FORMATO, a.conferir("POST", "/acoes/daily", "9".repeat(13), NONCE, CORPO, VETOR));
        assertEquals(Resultado.FORMATO, a.conferir("POST", "/acoes/daily", "" + AGORA, "curto", CORPO, VETOR));
        assertEquals(Resultado.FORMATO, a.conferir("POST", "/acoes/daily", "" + AGORA, NONCE, CORPO, VETOR.toUpperCase()));
        assertEquals(Resultado.FORMATO, a.conferir("POST", "/acoes/daily", "" + AGORA, NONCE, CORPO, VETOR + "00"));
    }

    @Test
    void assinaturaErradaNaoGastaNonce() {
        // Quem não tem a chave não consegue "queimar" nonces nem lotar a lista
        Assinatura a = nova(AGORA);
        assertEquals(Resultado.ASSINATURA_ERRADA, a.conferir("POST", "/acoes/daily", "" + AGORA, NONCE, CORPO, "0".repeat(64)));
        assertEquals(Resultado.OK, a.conferir("POST", "/acoes/daily", "" + AGORA, NONCE, CORPO, VETOR));
    }
}
