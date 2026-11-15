package io.github.jjoseffelipe2028.bracos.seguranca;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/** Ticket 080: 5 negados em 1 minuto = 1 disparo. Relógio FALSO: o teste não espera 1 minuto de verdade. */
@ExtendWith(OutputCaptureExtension.class)
class AlarmeDeTentativasTest {

    /** Relógio que o teste anda na mão. */
    static final class RelogioFalso extends Clock {
        private Instant agora = Instant.parse("2026-11-15T17:00:00Z");

        void andar(Duration d) {
            agora = agora.plus(d);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return agora;
        }
    }

    private final RelogioFalso relogio = new RelogioFalso();
    private final AlarmeDeTentativas alarme = new AlarmeDeTentativas(relogio);

    @Test
    void quatroNegados_naoDispara() {
        for (int i = 0; i < 4; i++) {
            assertFalse(alarme.negado());
        }
        assertEquals(0, alarme.estado().disparos());
        assertEquals(4, alarme.estado().negadosNoMinuto());
        assertEquals("", alarme.estado().ultimoDisparo());
    }

    @Test
    void cincoEmUmMinuto_disparaUmaVez_eZeraAJanela() {
        for (int i = 0; i < 4; i++) {
            assertFalse(alarme.negado());
            relogio.andar(Duration.ofSeconds(10));
        }
        assertTrue(alarme.negado(), "o 5º negado em 40 s dispara");
        AlarmeDeTentativas.Estado e = alarme.estado();
        assertEquals(1, e.disparos());
        assertEquals("15/11 14:00", e.ultimoDisparo()); // horário de Brasília
        assertEquals(0, e.negadosNoMinuto(), "depois do disparo a janela começa de novo");
        assertFalse(alarme.negado(), "o 6º negado não dispara de novo sozinho");
    }

    @Test
    void cincoEspalhadosEmDoisMinutos_naoDispara() {
        for (int i = 0; i < 5; i++) {
            assertFalse(alarme.negado());
            relogio.andar(Duration.ofSeconds(25)); // 1º e 5º ficam a 100 s de distância
        }
        assertEquals(0, alarme.estado().disparos());
    }

    @Test
    void exatamenteUmMinutoDepois_oPrimeiroJaSaiuDaJanela() {
        alarme.negado();
        relogio.andar(Duration.ofSeconds(60));
        for (int i = 0; i < 3; i++) {
            alarme.negado();
        }
        assertFalse(alarme.negado(), "o primeiro, de 60 s atrás, não conta mais");
        assertTrue(alarme.negado());
    }

    @Test
    void dezSeguidos_doisDisparos() {
        for (int i = 0; i < 10; i++) {
            alarme.negado();
        }
        assertEquals(2, alarme.estado().disparos());
    }

    @Test
    void logTemSoALinhaFixa(CapturedOutput saida) {
        for (int i = 0; i < 5; i++) {
            alarme.negado();
        }
        assertTrue(saida.getOut().contains("alarme=tentativas negados=5 janela=60s disparos=1"));
    }

    @Test
    void duasThreadsAoMesmoTempo_naoPerdemContagem() throws Exception {
        CountDownLatch largada = new CountDownLatch(1);
        List<Thread> threads = new ArrayList<>();
        for (int t = 0; t < 4; t++) {
            Thread th = new Thread(() -> {
                try {
                    largada.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                for (int i = 0; i < 25; i++) {
                    alarme.negado();
                }
            });
            th.start();
            threads.add(th);
        }
        largada.countDown();
        for (Thread th : threads) {
            th.join();
        }
        assertEquals(20, alarme.estado().disparos(), "100 negados = 20 disparos, sem perder nenhum");
    }
}
