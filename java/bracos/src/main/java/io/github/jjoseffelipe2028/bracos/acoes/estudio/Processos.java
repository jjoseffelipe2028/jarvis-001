package io.github.jjoseffelipe2028.bracos.acoes.estudio;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Roda um programa do Estúdio (Tickets 047 e 048) do jeito seguro: 🔒
 * - Sem shell: lista de argumentos fixa, nada é "interpretado".
 * - Entrada vazia (o programa não espera nada do teclado), erros descartados (não vazam para a tela).
 * - Tempo máximo: passou, o programa é derrubado.
 * - Saída limitada: ou derruba (agentes), ou guarda só o FIM (o produtor de cortes fala muito).
 * - {@code semSegredos}: tira do ambiente as variáveis JARVIS_* e tudo com TOKEN/SECRET/KEY/SENHA
 *   (os agentes de IA nunca recebem os segredos do Jarvis). O produtor (nosso código) precisa das pastas.
 */
public final class Processos {

    private static final Pattern SEGREDO = Pattern.compile("(?i)^(jarvis_.*|.*(token|secret|senha|password|passwd|key|credential).*)$");

    private Processos() {
    }

    public static String rodar(List<String> comando, Path pasta, Duration limite, boolean semSegredos,
                               int limiteSaida, boolean guardarSoOFim) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(comando).directory(pasta.toFile())
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .redirectInput(ProcessBuilder.Redirect.from(nulo()));
        if (semSegredos) {
            pb.environment().keySet().removeIf(nome -> SEGREDO.matcher(nome).matches());
        }
        pb.environment().put("PYTHONIOENCODING", "utf-8");
        Process processo = pb.start();
        byte[] saida;
        try (InputStream in = processo.getInputStream()) {
            saida = ler(in, processo, limite, limiteSaida, guardarSoOFim);
        }
        if (!processo.waitFor(5, TimeUnit.SECONDS) || processo.exitValue() != 0) {
            processo.destroyForcibly();
            throw new SaidaComErro(new String(saida, StandardCharsets.UTF_8));
        }
        return new String(saida, StandardCharsets.UTF_8).strip();
    }

    /** O programa terminou com erro. Guarda o que ele disse (para a tela mostrar só as últimas linhas, limpas). */
    public static final class SaidaComErro extends IOException {
        private final String saida;

        SaidaComErro(String saida) {
            super("programa terminou com erro");
            this.saida = saida;
        }

        public String saida() {
            return saida;
        }
    }

    private static byte[] ler(InputStream in, Process processo, Duration limite, int limiteSaida, boolean guardarSoOFim)
            throws IOException, InterruptedException {
        long fim = System.nanoTime() + limite.toNanos();
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] pedaco = new byte[8192];
        while (true) {
            if (System.nanoTime() > fim) {
                processo.destroyForcibly();
                throw new IOException("tempo esgotado");
            }
            int disponivel = in.available();
            if (disponivel > 0 || !processo.isAlive()) {
                int lidos = in.read(pedaco);
                if (lidos < 0) {
                    break;
                }
                if (buffer.size() + lidos > limiteSaida) {
                    if (!guardarSoOFim) {
                        processo.destroyForcibly();
                        throw new IOException("saida grande demais");
                    }
                    byte[] tudo = buffer.toByteArray();
                    buffer.reset();
                    int manter = Math.max(0, limiteSaida / 2 - lidos);
                    buffer.write(Arrays.copyOfRange(tudo, tudo.length - Math.min(manter, tudo.length), tudo.length));
                }
                buffer.write(pedaco, 0, lidos);
            } else {
                Thread.sleep(200);
            }
        }
        return buffer.toByteArray();
    }

    private static File nulo() {
        return new File(System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win") ? "NUL" : "/dev/null");
    }
}
