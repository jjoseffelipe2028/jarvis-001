package io.github.jjoseffelipe2028.bracos.central;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Chave de uso único da Central (Ticket 015). */
class ChaveDaCentralTest {

    @TempDir
    Path pasta;

    @Test
    void semArquivoConfigurado_desligadaERecusaTudo() {
        ChaveDaCentral chave = new ChaveDaCentral("");
        assertFalse(chave.ligada());
        assertFalse(chave.usar("a".repeat(64)));
    }

    @Test
    void criaChaveDe256BitsNoArquivo() throws IOException {
        Path arquivo = pasta.resolve(".central-chave");
        new ChaveDaCentral(arquivo.toString());
        assertTrue(Files.readString(arquivo).matches("[0-9a-f]{64}"));
    }

    @Test
    void chaveCerta_funcionaUmaVezSo_eJaETrocada() throws IOException {
        Path arquivo = pasta.resolve(".central-chave");
        ChaveDaCentral chave = new ChaveDaCentral(arquivo.toString());
        String primeira = Files.readString(arquivo);

        assertTrue(chave.usar(primeira));
        assertFalse(chave.usar(primeira), "a mesma chave não pode ser usada duas vezes");

        String nova = Files.readString(arquivo);
        assertNotEquals(primeira, nova, "o arquivo ganha uma chave nova");
        assertTrue(chave.usar(nova));
    }

    @Test
    void chaveErradaOuGigante_recusa() throws IOException {
        Path arquivo = pasta.resolve(".central-chave");
        ChaveDaCentral chave = new ChaveDaCentral(arquivo.toString());
        assertFalse(chave.usar("0".repeat(64)));
        assertFalse(chave.usar(null));
        assertFalse(chave.usar("a".repeat(10_000)));
        assertTrue(chave.usar(Files.readString(arquivo)), "tentativas erradas não estragam a chave certa");
    }
}
