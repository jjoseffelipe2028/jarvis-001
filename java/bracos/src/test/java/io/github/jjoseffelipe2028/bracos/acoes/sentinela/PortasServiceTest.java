package io.github.jjoseffelipe2028.bracos.acoes.sentinela;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import io.github.jjoseffelipe2028.bracos.erros.AcaoIndisponivelException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/** Ticket 021: o serviço da Sentinela e o executor com travas. 🛡️ */
class PortasServiceTest {

    private static final String JAVA = ProcessHandle.current().info().command().orElseThrow();

    @Test
    void usaOTextoDoNetstat() {
        PortasService s = new PortasService(8080, () -> "  TCP    0.0.0.0:445   0.0.0.0:0   LISTENING\n");
        assertEquals(1, s.vigiar().abertasParaARede());
    }

    @Test
    void netstatSoPeloCaminhoCompletoDoSistema(@TempDir Path pasta) throws IOException {
        assertTrue(PortasService.netstatDoSistema("Windows").isEmpty(), "pasta relativa: recusa");
        assertTrue(PortasService.netstatDoSistema(pasta.toString()).isEmpty(), "sem o arquivo: recusa");
        Path sys32 = Files.createDirectories(pasta.resolve("System32"));
        Files.writeString(sys32.resolve("NETSTAT.EXE"), "x");
        assertEquals(sys32.resolve("NETSTAT.EXE"), PortasService.netstatDoSistema(pasta.toString()).orElseThrow());
    }

    @Test
    void argumentosFixos() {
        assertEquals(List.of("-a", "-n"), PortasService.ARGUMENTOS);
    }

    @Test
    void executor_devolveASaida() {
        // "java --version" escreve na saída padrão e termina rápido
        String saida = ExecutorFixo.rodar(List.of(JAVA, "--version"), Duration.ofSeconds(20), 64 * 1024);
        assertTrue(saida.toLowerCase().contains("jdk") || saida.toLowerCase().contains("java"));
    }

    @Test
    void executor_saidaGrandeDemais_recusa() {
        assertThrows(AcaoIndisponivelException.class,
                () -> ExecutorFixo.rodar(List.of(JAVA, "--version"), Duration.ofSeconds(20), 5));
    }

    @Test
    void executor_programaQueTrava_eMorto(@TempDir Path pasta) throws IOException {
        Path dorme = pasta.resolve("Dorme.java");
        Files.writeString(dorme, "class Dorme { public static void main(String[] a) throws Exception { Thread.sleep(60000); } }");
        long inicio = System.nanoTime();
        assertThrows(AcaoIndisponivelException.class,
                () -> ExecutorFixo.rodar(List.of(JAVA, dorme.toString()), Duration.ofMillis(300), 1024));
        assertTrue(System.nanoTime() - inicio < Duration.ofSeconds(10).toNanos(), "não esperou o programa travado");
    }

    @Test
    @EnabledOnOs(OS.WINDOWS) // no CI (Linux) fica pulado; no PC do José roda o netstat de verdade
    void noWindows_netstatDeVerdade_funciona() {
        PortasService.Relatorio r = new PortasService(8080).vigiar();
        assertTrue(r.escutando() > 0, "todo Windows tem alguma porta escutando (ex.: 135)");
        assertTrue(r.portas().size() <= LeitorDeNetstat.MAXIMO_NA_LISTA);
    }

    @Test
    void executor_programaQueNaoExiste_503() {
        assertThrows(AcaoIndisponivelException.class,
                () -> ExecutorFixo.rodar(List.of("programa-que-nao-existe-jarvis"), Duration.ofSeconds(2), 1024));
    }
}
