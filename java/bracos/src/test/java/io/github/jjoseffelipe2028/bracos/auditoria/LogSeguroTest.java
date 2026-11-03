package io.github.jjoseffelipe2028.bracos.auditoria;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** JUnit puro, sem Spring. */
class LogSeguroTest {

    @Test
    void nuloViraTraco() {
        assertEquals("-", LogSeguro.limpar(null));
    }

    @Test
    void textoNormalFicaIgual() {
        assertEquals("/acoes/daily", LogSeguro.limpar("/acoes/daily"));
    }

    @ParameterizedTest(name = "caractere de controle {index} vira underscore")
    @ValueSource(strings = {"\r", "\n", "\t", "\u001b", " ", " ", "\u0085"})
    void caractereDeControleViraUnderscore(String controle) {
        assertEquals("a_b", LogSeguro.limpar("a" + controle + "b"));
    }

    @Test
    void cemCaracteresNaoCorta() {
        String cem = "a".repeat(100);
        assertEquals(cem, LogSeguro.limpar(cem));
    }

    @Test
    void cemEUmCaracteresCortaPara100MaisReticencias() {
        assertEquals("a".repeat(100) + "...", LogSeguro.limpar("a".repeat(101)));
    }

    @Test
    void controlePertoDoLimite_limpaAntesDeCortar() {
        String resultado = LogSeguro.limpar("a".repeat(99) + "\n" + "bbb");
        assertEquals("a".repeat(99) + "_" + "...", resultado);
        assertFalse(resultado.contains("\n"));
    }
}
