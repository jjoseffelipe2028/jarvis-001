package io.github.jjoseffelipe2028.bracos.central;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;

/** Quadro de eventos da Central (Ticket 017). */
class QuadroDeEventosTest {

    private static final Clock RELOGIO = Clock.fixed(Instant.parse("2026-11-09T23:41:05Z"), ZoneId.of("America/Sao_Paulo"));

    @Test
    void vazio_naoTemEventos() {
        QuadroDeEventos.Leitura l = new QuadroDeEventos(RELOGIO).depois(0);
        assertTrue(l.eventos().isEmpty());
        assertEquals(0, l.ultimo());
    }

    @Test
    void devolveSoOsNovos_emOrdem_comHoraDeBrasilia() {
        QuadroDeEventos q = new QuadroDeEventos(RELOGIO);
        q.registrar("daily", "ok", "python");
        q.registrar("pendencias", "recusado", "desconhecido");
        q.registrar("git-status", "ok", "python");

        QuadroDeEventos.Leitura l = q.depois(1);
        assertEquals(3, l.ultimo());
        assertEquals(2, l.eventos().size());
        assertEquals("pendencias", l.eventos().get(0).acao());
        assertEquals("recusado", l.eventos().get(0).resultado());
        assertEquals("git-status", l.eventos().get(1).acao());
        assertEquals("20:41:05", l.eventos().get(1).hora());
        assertTrue(q.depois(3).eventos().isEmpty(), "nada novo depois do último");
    }

    @Test
    void guardaNoMaximo30_osMaisVelhosSaem() {
        QuadroDeEventos q = new QuadroDeEventos(RELOGIO);
        for (int i = 0; i < QuadroDeEventos.MAXIMO + 12; i++) {
            q.registrar("pendencias", "ok", "python");
        }
        QuadroDeEventos.Leitura l = q.depois(0);
        assertEquals(QuadroDeEventos.MAXIMO, l.eventos().size());
        assertEquals(13, l.eventos().get(0).seq(), "os 12 primeiros já saíram");
        assertEquals(QuadroDeEventos.MAXIMO + 12, l.ultimo());
    }
}
