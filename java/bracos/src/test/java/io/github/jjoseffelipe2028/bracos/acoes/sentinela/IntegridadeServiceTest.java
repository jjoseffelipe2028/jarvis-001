package io.github.jjoseffelipe2028.bracos.acoes.sentinela;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;
import java.util.stream.Collectors;

import io.github.jjoseffelipe2028.bracos.erros.AcaoIndisponivelException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Ticket 022: o vigia de integridade percebe quem mexeu nas travas. 🔏 */
class IntegridadeServiceTest {

    private static final Clock RELOGIO = Clock.fixed(Instant.parse("2026-11-10T05:10:00Z"), ZoneId.of("America/Sao_Paulo"));

    /** Projeto falso com as travas (exceto o .gitignore, que "aparece" depois em um teste). */
    private static Path projeto(Path pasta) throws IOException {
        Files.createDirectories(pasta.resolve(".claude/hooks"));
        Files.createDirectories(pasta.resolve("java/bracos/src/main/resources"));
        Files.createDirectories(pasta.resolve(".git"));
        Files.writeString(pasta.resolve("CLAUDE.md"), "# regras");
        Files.writeString(pasta.resolve(".claude/settings.json"), "{}");
        Files.writeString(pasta.resolve(".claude/hooks/guarda.mjs"), "// guarda");
        Files.writeString(pasta.resolve("java/bracos/src/main/resources/application.properties"), "server.address=127.0.0.1");
        return pasta;
    }

    private static Map<String, String> estados(IntegridadeService.Relatorio r) {
        return r.arquivos().stream().collect(Collectors.toMap(IntegridadeService.Arquivo::nome, IntegridadeService.Arquivo::estado));
    }

    @Test
    void semMudanca_tudoIgual_eHoraDaFoto(@TempDir Path pasta) throws IOException {
        IntegridadeService s = new IntegridadeService(projeto(pasta), RELOGIO);
        IntegridadeService.Relatorio r = s.conferir();
        assertEquals(5, r.vigiados());
        assertEquals(0, r.alterados());
        assertEquals("02:10", r.desde(), "hora de Brasília em que a API tirou a foto");
        assertEquals(12, r.arquivos().get(0).hash().length(), "só 12 letras do hash saem");
    }

    @Test
    void percebeAlteradoSumiuEApareceu(@TempDir Path pasta) throws IOException {
        IntegridadeService s = new IntegridadeService(projeto(pasta), RELOGIO);
        Files.writeString(pasta.resolve(".claude/hooks/guarda.mjs"), "// guarda DESLIGADA por alguém");
        Files.delete(pasta.resolve("CLAUDE.md"));
        Files.writeString(pasta.resolve(".gitignore"), ".env");
        IntegridadeService.Relatorio r = s.conferir();
        Map<String, String> e = estados(r);
        assertEquals(IntegridadeService.ALTERADO, e.get(".claude/hooks/guarda.mjs"));
        assertEquals(IntegridadeService.SUMIU, e.get("CLAUDE.md"));
        assertEquals(IntegridadeService.APARECEU, e.get(".gitignore"));
        assertEquals(IntegridadeService.IGUAL, e.get(".claude/settings.json"));
        assertEquals(3, r.alterados());
    }

    @Test
    void mudarUmEspacoJaMudaOHash(@TempDir Path pasta) throws IOException {
        IntegridadeService s = new IntegridadeService(projeto(pasta), RELOGIO);
        Files.writeString(pasta.resolve(".claude/settings.json"), "{ }");
        assertEquals(IntegridadeService.ALTERADO, estados(s.conferir()).get(".claude/settings.json"));
    }

    @Test
    void pastaNoLugarDoArquivo_ouGrandeDemais_eEstranho(@TempDir Path pasta) throws IOException {
        IntegridadeService s = new IntegridadeService(projeto(pasta), RELOGIO);
        Files.delete(pasta.resolve("CLAUDE.md"));
        Files.createDirectory(pasta.resolve("CLAUDE.md"));
        Files.write(pasta.resolve(".claude/settings.json"), new byte[(int) IntegridadeService.LIMITE_BYTES + 1]);
        Map<String, String> e = estados(s.conferir());
        assertEquals(IntegridadeService.ESTRANHO, e.get("CLAUDE.md"));
        assertEquals(IntegridadeService.ESTRANHO, e.get(".claude/settings.json"));
    }

    @Test
    void atalhoNaoESeguido(@TempDir Path pasta) throws IOException {
        IntegridadeService s = new IntegridadeService(projeto(pasta), RELOGIO);
        Path alvo = Files.writeString(pasta.resolve("outro.txt"), "x");
        Files.delete(pasta.resolve(".claude/settings.json"));
        try {
            Files.createSymbolicLink(pasta.resolve(".claude/settings.json"), alvo);
        } catch (IOException | UnsupportedOperationException e) {
            assumeTrue(false, "este Windows não deixa criar atalho sem permissão de admin");
        }
        assertEquals(IntegridadeService.ESTRANHO, estados(s.conferir()).get(".claude/settings.json"));
    }

    @Test
    void semProjeto_503(@TempDir Path pasta) {
        assertNull(IntegridadeService.acharProjeto(pasta.toString()), "sem CLAUDE.md e .git não é o projeto");
        IntegridadeService s = new IntegridadeService(null, RELOGIO);
        assertThrows(AcaoIndisponivelException.class, s::conferir);
    }

    @Test
    void achaOProjetoDeVerdade_quandoRodaDentroDeJavaBracos() {
        // Os testes rodam em java/bracos (como a API): duas pastas acima é a raiz do jarvis-001
        assertNotNull(IntegridadeService.acharProjeto(""), "a raiz do projeto tem CLAUDE.md e .git");
    }
}
