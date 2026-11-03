package io.github.jjoseffelipe2028.bracos.auditoria;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ClienteTest {

    @Test
    void pythonECentralPassam() {
        assertEquals("python", Cliente.de("python"));
        assertEquals("central", Cliente.de("central"));
    }

    @Test
    void clienteForaDaListaViraDesconhecido() {
        assertEquals("desconhecido", Cliente.de("hacker"));
        assertEquals("desconhecido", Cliente.de(""));
        assertEquals("desconhecido", Cliente.de("PYTHON"));
        assertEquals("desconhecido", Cliente.de(null));
    }
}
