package io.github.jjoseffelipe2028.bracos.central;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

/** Crachás da Central (Ticket 015). */
class SessoesDaCentralTest {

    /** Relógio que dá para adiantar no teste. */
    static final class RelogioDeTeste extends Clock {
        Instant agora = Instant.parse("2026-11-09T22:00:00Z");

        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return agora; }
    }

    @Test
    void crachaNovoEValido_eDe64Hex() {
        SessoesDaCentral s = new SessoesDaCentral();
        String c = s.criar();
        assertTrue(c.matches("[0-9a-f]{64}"));
        assertTrue(s.valido(c));
    }

    @Test
    void crachaVence12HorasDepois() {
        RelogioDeTeste relogio = new RelogioDeTeste();
        SessoesDaCentral s = new SessoesDaCentral(relogio);
        String c = s.criar();
        relogio.agora = relogio.agora.plus(Duration.ofHours(12)).minusSeconds(1);
        assertTrue(s.valido(c));
        relogio.agora = relogio.agora.plusSeconds(1);
        assertFalse(s.valido(c));
    }

    @Test
    void noMaximo5_oMaisVelhoSai() {
        RelogioDeTeste relogio = new RelogioDeTeste();
        SessoesDaCentral s = new SessoesDaCentral(relogio);
        String primeiro = s.criar();
        for (int i = 0; i < SessoesDaCentral.MAXIMO; i++) {
            relogio.agora = relogio.agora.plusSeconds(1);
            s.criar();
        }
        assertFalse(s.valido(primeiro));
    }

    @Test
    void crachaInventadoOuTorto_invalido() {
        SessoesDaCentral s = new SessoesDaCentral();
        s.criar();
        assertFalse(s.valido(null));
        assertFalse(s.valido(""));
        assertFalse(s.valido("0".repeat(64)));
        assertFalse(s.valido("x".repeat(5000)));
    }
}
