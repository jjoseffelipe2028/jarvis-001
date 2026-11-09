package io.github.jjoseffelipe2028.bracos.acoes.git;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.github.jjoseffelipe2028.bracos.acoes.PastaSegura;
import io.github.jjoseffelipe2028.bracos.erros.AcaoIndisponivelException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Diz se o repositório tem algo sem commit. 🔒
 * É o PRIMEIRO braço que roda um programa (o git), então as travas são mais fortes:
 * - Comando FIXO no código: nada do pedido entra nele (anti injeção de comando).
 * - Sem terminal no meio (ProcessBuilder direto): ";", "&&" e "|" não viram comando.
 * - Pasta do repositório vem do .env (JARVIS_PASTA_GIT), nunca do pedido.
 * - core.fsmonitor desligado: um .git/config malicioso não consegue rodar programa no "git status".
 * - Variáveis GIT_* de fora são apagadas (ninguém troca o repositório por variável de ambiente).
 * - Tempo limite de 5 s e saída de no máximo 64 KB: travou ou cresceu demais → mata o processo.
 * - A resposta tem só NÚMEROS e o nome da branch filtrado. Nome de arquivo nunca sai.
 */
@Service
public class GitStatusService {

    static final Duration LIMITE_TEMPO = Duration.ofSeconds(5);
    static final int LIMITE_SAIDA_BYTES = 64 * 1024;

    /** Os argumentos, fixos. Nenhuma parte vem de fora. O executável é achado por resolverGit(). */
    static final List<String> ARGUMENTOS_GIT = List.of(
            "-c", "core.fsmonitor=false",   // não roda o "vigia" configurável (executaria programa)
            "-c", "core.quotePath=true",    // nome estranho de arquivo sai entre aspas, numa linha só
            "--no-optional-locks",          // só lê: não trava o repositório
            "status", "--porcelain=v1", "--branch",
            "--ignore-submodules=all");     // submódulo teria config própria (outro jeito de rodar programa)

    private static final boolean WINDOWS =
            System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");

    /** Um git por vez: 200 pedidos juntos não viram 200 processos (anti DoS). */
    private final Semaphore umPorVez = new Semaphore(1);

    // Cabeçalho do --branch: "## main...origin/main [ahead 1, behind 2]" ou "## No commits yet on main"
    private static final Pattern CABECALHO = Pattern.compile("^## (?:No commits yet on |Initial commit on )?(.+?)(?:\\.\\.\\.\\S+)?(?: \\[(.+)])?$");
    private static final Pattern A_FRENTE = Pattern.compile("ahead (\\d+)");
    private static final Pattern ATRAS = Pattern.compile("behind (\\d+)");
    // Nome de branch que pode sair na resposta: letras, números e . _ / - (até 100)
    private static final Pattern BRANCH_SEGURA = Pattern.compile("[A-Za-z0-9._/-]{1,100}");

    /** O que o Jarvis responde. Só números e a branch filtrada. */
    public record Status(String branch, boolean limpo, int alterados, int novos, int aFrente, int atras) {
    }

    private final String pastaConfigurada;
    private final List<String> comandoDeTeste; // null = git de verdade
    private final Duration limiteTempo;

    @Autowired
    public GitStatusService(@Value("${jarvis.pastas.git:}") String pastaConfigurada) {
        this.pastaConfigurada = pastaConfigurada;
        this.comandoDeTeste = null;
        this.limiteTempo = LIMITE_TEMPO;
    }

    /** Usado nos testes, para trocar o comando (ex.: um programa que trava) e o tempo limite. */
    GitStatusService(String pastaConfigurada, List<String> comando, Duration limiteTempo) {
        this.pastaConfigurada = pastaConfigurada;
        this.comandoDeTeste = List.copyOf(comando);
        this.limiteTempo = limiteTempo;
    }

    public Status consultar() {
        Path repo = PastaSegura.base(pastaConfigurada);
        if (!umPorVez.tryAcquire()) {
            throw new AcaoIndisponivelException("git ocupado");
        }
        try {
            return ler(executar(repo, montarComando(repo)));
        } finally {
            umPorVez.release();
        }
    }

    private List<String> montarComando(Path repo) {
        if (comandoDeTeste != null) {
            return comandoDeTeste;
        }
        Path git = resolverGit(System.getenv("PATH"), repo)
                .orElseThrow(() -> new AcaoIndisponivelException("git indisponivel"));
        List<String> cmd = new ArrayList<>();
        cmd.add(git.toString());
        cmd.addAll(ARGUMENTOS_GIT);
        return cmd;
    }

    /**
     * Acha o git pelo CAMINHO COMPLETO, olhando só pastas absolutas do PATH. 🔒
     * Sem isso, o Windows procuraria "git.exe" primeiro na pasta atual do Jarvis:
     * um git.exe plantado ali rodaria no lugar do verdadeiro.
     * Também recusa um git que esteja DENTRO do repositório vigiado.
     */
    static Optional<Path> resolverGit(String path, Path repo) {
        if (path == null || path.isBlank()) {
            return Optional.empty();
        }
        String nome = WINDOWS ? "git.exe" : "git";
        for (String pasta : path.split(java.io.File.pathSeparator)) {
            try {
                Path dir = Path.of(pasta.strip());
                if (pasta.isBlank() || !dir.isAbsolute()) {
                    continue; // "." ou pasta relativa: ignora
                }
                Path candidato = dir.resolve(nome).normalize();
                if (candidato.startsWith(repo.normalize())) {
                    continue;
                }
                if (Files.isRegularFile(candidato) && Files.isExecutable(candidato)) {
                    return Optional.of(candidato);
                }
            } catch (InvalidPathException e) {
                // entrada estranha no PATH: ignora
            }
        }
        return Optional.empty();
    }

    /** Roda o comando fixo dentro da pasta do repositório e devolve a saída (texto). */
    private String executar(Path repo, List<String> comando) {
        ProcessBuilder pb = new ProcessBuilder(comando)
                .directory(repo.toFile())
                .redirectErrorStream(false)
                .redirectError(ProcessBuilder.Redirect.DISCARD); // erro do git pode ter caminho: descarta
        limparAmbiente(pb.environment());

        Process processo;
        try {
            processo = pb.start();
            processo.getOutputStream().close(); // o git não recebe nada pela entrada
        } catch (IOException e) {
            throw new AcaoIndisponivelException("git indisponivel");
        }

        // Lê a saída em paralelo (com limite), enquanto o relógio conta o tempo
        FutureTask<byte[]> leitura = new FutureTask<>(() -> lerComLimite(processo.getInputStream()));
        Thread.ofVirtual().start(leitura);
        // Ticket 008: anota os filhos e netos ENQUANTO o git está vivo (depois que ele sai,
        // não dá mais para achar os netos órfãos). No fim, mata todos os anotados.
        java.util.Set<ProcessHandle> arvore = new java.util.HashSet<>();
        try {
            boolean terminou = esperarAnotandoFilhos(processo, arvore);
            if (!terminou) {
                throw new AcaoIndisponivelException("git demorou demais");
            }
            byte[] saida = esperar(leitura);
            if (saida.length > LIMITE_SAIDA_BYTES) {
                throw new AcaoIndisponivelException("saida do git grande demais");
            }
            if (processo.exitValue() != 0) {
                // Ex.: a pasta não é um repositório git
                throw new AcaoIndisponivelException("repositorio indisponivel");
            }
            return new String(saida, StandardCharsets.UTF_8);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AcaoIndisponivelException("git interrompido");
        } finally {
            // Em qualquer saída (erro, demora, saída gigante): ninguém da árvore fica vivo.
            // Só pergunta os filhos se o git ainda está VIVO: morto, o número (PID) dele pode já
            // ser de outro programa do José, e mataríamos os filhos errados.
            if (processo.isAlive()) {
                processo.descendants().forEach(arvore::add);
            }
            arvore.forEach(ProcessHandle::destroyForcibly);
            processo.destroyForcibly();
            // Fechar a saída destrava a leitura (cancel sozinho não solta um cano preso)
            try {
                processo.getInputStream().close();
            } catch (IOException e) {
                // já fechado
            }
            leitura.cancel(true);
        }
    }

    /** Espera o git terminar (até o limite), anotando a árvore de processos a cada 50 ms. */
    private boolean esperarAnotandoFilhos(Process processo, java.util.Set<ProcessHandle> arvore)
            throws InterruptedException {
        long fim = System.nanoTime() + limiteTempo.toNanos();
        while (true) {
            if (processo.isAlive()) {
                processo.descendants().forEach(arvore::add);
            }
            long resta = fim - System.nanoTime();
            if (resta <= 0) {
                return false;
            }
            if (processo.waitFor(Math.min(TimeUnit.NANOSECONDS.toMillis(resta) + 1, 50), TimeUnit.MILLISECONDS)) {
                return true;
            }
        }
    }

    /** Apaga GIT_* herdadas (podem trocar o repositório ou a config) e desliga perguntas. */
    static void limparAmbiente(Map<String, String> ambiente) {
        ambiente.keySet().removeIf(k -> k.toUpperCase(Locale.ROOT).startsWith("GIT_"));
        ambiente.put("GIT_TERMINAL_PROMPT", "0"); // nunca pede senha
        ambiente.put("GIT_OPTIONAL_LOCKS", "0");
        // Ticket 008: só a config do PRÓPRIO repositório vale. A do sistema e a global (~/.gitconfig)
        // são ignoradas: uma chave perigosa lá não entra no "git status" do Jarvis.
        ambiente.put("GIT_CONFIG_NOSYSTEM", "1");
        ambiente.put("GIT_CONFIG_GLOBAL", "/dev/null");
        ambiente.put("LC_ALL", "C");              // mensagens em inglês fixo: o leitor abaixo depende delas
    }

    /** Lê no máximo LIMITE + 1 bytes: se passou do limite, a gente percebe sem ler tudo. */
    private static byte[] lerComLimite(InputStream entrada) throws IOException {
        try (entrada) {
            return entrada.readNBytes(LIMITE_SAIDA_BYTES + 1);
        }
    }

    private static byte[] esperar(Future<byte[]> leitura) throws InterruptedException {
        try {
            return leitura.get(1, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            throw new UncheckedIOException("falha ao ler o git", new IOException(e.getCause()));
        } catch (TimeoutException e) {
            // O git terminou mas a saída não fechou (ex.: um filho segurando): trata como travado
            throw new AcaoIndisponivelException("git demorou demais");
        }
    }

    /**
     * Transforma a saída do "git status --porcelain=v1 --branch" em números.
     * Nenhum nome de arquivo é guardado: só contamos as linhas.
     */
    static Status ler(String saida) {
        String branch = "?";
        int alterados = 0;
        int novos = 0;
        int aFrente = 0;
        int atras = 0;

        for (String linha : saida.split("\\r?\\n")) {
            if (linha.isEmpty()) {
                continue;
            }
            if (linha.startsWith("## ")) {
                Matcher m = CABECALHO.matcher(linha);
                if (m.matches()) {
                    branch = filtrarBranch(m.group(1));
                    if (m.group(2) != null) {
                        aFrente = numero(A_FRENTE, m.group(2));
                        atras = numero(ATRAS, m.group(2));
                    }
                }
            } else if (linha.startsWith("??")) {
                novos++;
            } else if (!linha.startsWith("!!")) {
                alterados++;
            }
        }
        return new Status(branch, alterados == 0 && novos == 0, alterados, novos, aFrente, atras);
    }

    /** Branch com caractere estranho (ex.: de um repositório malicioso) não sai como veio. */
    static String filtrarBranch(String nome) {
        if (nome.startsWith("HEAD (no branch)")) {
            return "HEAD-solto";
        }
        return BRANCH_SEGURA.matcher(nome).matches() ? nome : "?";
    }

    private static int numero(Pattern p, String texto) {
        Matcher m = p.matcher(texto);
        if (!m.find()) {
            return 0;
        }
        try {
            return Integer.parseInt(m.group(1));
        } catch (NumberFormatException e) {
            return Integer.MAX_VALUE; // número absurdo: não quebra a resposta
        }
    }
}
