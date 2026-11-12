package io.github.jjoseffelipe2028.bracos.acoes.video;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Ticket 025: as mesmas regras do porteiro em Python (cortador.py). Testa os LIMITES exatos. */
class PorteiroDeCortesTest {

    private final PorteiroDeCortes porteiro = new PorteiroDeCortes();

    private PorteiroDeCortes.Liberacao pedir(int inicio, int fim, int duracaoVideo, String arroba) {
        return porteiro.liberar(new PedidoDeCorte(inicio, fim, duracaoVideo, arroba));
    }

    @Test
    void corteNormal_passa() {
        PorteiroDeCortes.Liberacao l = pedir(60, 85, 3600, "@amigo");
        assertTrue(l.libera());
        assertEquals("ok", l.motivo());
    }

    @Test
    void fimAntesDoComeco_barra() {
        assertFalse(pedir(85, 60, 3600, "@amigo").libera());
        assertFalse(pedir(60, 60, 3600, "@amigo").libera());
    }

    @Test
    void alemDoFimDoVideo_barra() {
        assertFalse(pedir(3590, 3620, 3600, "@amigo").libera());
        assertTrue(pedir(3580, 3600, 3600, "@amigo").libera()); // termina exatamente no fim: passa
    }

    @Test
    void menosDe13s_barra_e_13s_passa() {
        PorteiroDeCortes.Liberacao dezSegundos = pedir(20, 30, 3600, "@amigo");
        assertFalse(dezSegundos.libera());
        assertEquals("menos de 13 s", dezSegundos.motivo());
        assertTrue(pedir(20, 33, 3600, "@amigo").libera());
    }

    @Test
    void maisDe3min_barra_e_3min_passa() {
        assertFalse(pedir(0, 181, 3600, "@amigo").libera());
        assertTrue(pedir(0, 180, 3600, "@amigo").libera());
    }

    @Test
    void arrobaEstranho_barra() {
        for (String ruim : new String[] {null, "", "@", "@a b", "@a;b", "@{\\b1}", "@" + "a".repeat(31)}) {
            assertFalse(pedir(0, 20, 3600, ruim).libera(), "deveria barrar: " + ruim);
        }
        assertTrue(pedir(0, 20, 3600, "amigo_gta.ps5").libera()); // sem @ na frente também vale
    }

    @Test
    void comecoNegativo_barra() {
        assertFalse(pedir(-5, 20, 3600, "@amigo").libera());
    }
}
