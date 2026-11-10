package io.github.jjoseffelipe2028.bracos.acoes.sentinela;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import io.github.jjoseffelipe2028.bracos.erros.AcaoIndisponivelException;

/**
 * Roda UM programa com argumentos fixos e devolve a saída (Ticket 021). 🔒
 * Mesmas travas do git-status (Tickets 004 e 008):
 * - Sem terminal no meio (ProcessBuilder direto): ";", "&&" e "|" não viram comando.
 * - Não recebe nada pela entrada; o erro (stderr) é descartado.
 * - Tempo limite e tamanho máximo da saída: travou ou cresceu demais → mata o processo e os filhos.
 */
final class ExecutorFixo {

    private ExecutorFixo() {
    }

    static String rodar(List<String> comando, Duration limiteTempo, int limiteBytes) {
        ProcessBuilder pb = new ProcessBuilder(comando).redirectError(ProcessBuilder.Redirect.DISCARD);
        Process processo;
        try {
            processo = pb.start();
            processo.getOutputStream().close();
        } catch (IOException e) {
            throw new AcaoIndisponivelException("programa indisponivel");
        }

        FutureTask<byte[]> leitura = new FutureTask<>(() -> lerComLimite(processo.getInputStream(), limiteBytes));
        Thread.ofVirtual().start(leitura);
        try {
            if (!processo.waitFor(limiteTempo.toMillis(), TimeUnit.MILLISECONDS)) {
                throw new AcaoIndisponivelException("demorou demais");
            }
            byte[] saida = leitura.get(1, TimeUnit.SECONDS);
            if (saida.length > limiteBytes) {
                throw new AcaoIndisponivelException("saida grande demais");
            }
            if (processo.exitValue() != 0) {
                throw new AcaoIndisponivelException("programa falhou");
            }
            // ISO-8859-1 nunca dá erro de leitura; o leitor só usa a parte ASCII (TCP, números, ":")
            return new String(saida, StandardCharsets.ISO_8859_1);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AcaoIndisponivelException("interrompido");
        } catch (ExecutionException | TimeoutException e) {
            throw new AcaoIndisponivelException("falha ao ler a saida");
        } finally {
            if (processo.isAlive()) {
                processo.descendants().forEach(ProcessHandle::destroyForcibly);
            }
            processo.destroyForcibly();
            try {
                processo.getInputStream().close(); // destrava a leitura se ficou presa
            } catch (IOException e) {
                // já fechado
            }
            leitura.cancel(true);
        }
    }

    /** Lê no máximo limite + 1 bytes: se passou, a gente percebe sem ler tudo. */
    private static byte[] lerComLimite(InputStream entrada, int limiteBytes) throws IOException {
        try (entrada) {
            return entrada.readNBytes(limiteBytes + 1);
        }
    }
}
