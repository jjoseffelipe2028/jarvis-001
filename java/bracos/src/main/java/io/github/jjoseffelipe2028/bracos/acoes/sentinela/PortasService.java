package io.github.jjoseffelipe2028.bracos.acoes.sentinela;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.Semaphore;
import java.util.function.Supplier;

import io.github.jjoseffelipe2028.bracos.erros.AcaoIndisponivelException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Sentinela v1: vigia de portas (Ticket 021). 🛡️ Só OLHA e AVISA, nunca fecha nem bloqueia nada.
 * (Regra do José: IA com poder de agir vira arma se for enganada. Quem age é ele.)
 * - Comando FIXO: netstat -a -n, achado pelo caminho completo em %SystemRoot%\System32.
 *   Nada do pedido entra no comando.
 * - Um por vez, tempo limite de 8 s e saída de no máximo 512 KB (ExecutorFixo).
 * - Fora do Windows (ex.: o CI no Linux) → 503: a ação fica desligada.
 * - A resposta tem só números de porta, alcance e notas fixas (LeitorDeNetstat).
 */
@Service
public class PortasService {

    static final Duration LIMITE_TEMPO = Duration.ofSeconds(8);
    static final int LIMITE_SAIDA_BYTES = 512 * 1024;
    static final List<String> ARGUMENTOS = List.of("-a", "-n");

    private static final boolean WINDOWS =
            System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");

    /** Uma porta escutando e aberta para a rede. */
    public record PortaAberta(int porta, String alcance, String risco, String nota) {
    }

    /** O que a Sentinela responde. */
    public record Relatorio(int escutando, int soNoPc, int abertasParaARede, int alertas,
                            boolean jarvisSoNoPc, List<PortaAberta> portas) {
    }

    private final int portaDoJarvis;
    private final Supplier<String> netstat;
    private final Semaphore umPorVez = new Semaphore(1);

    @Autowired
    public PortasService(@Value("${server.port:8080}") int portaDoJarvis) {
        this(portaDoJarvis, () -> ExecutorFixo.rodar(comando(System.getenv("SystemRoot")), LIMITE_TEMPO, LIMITE_SAIDA_BYTES));
    }

    /** Usado nos testes: troca o netstat de verdade por um texto pronto. */
    PortasService(int portaDoJarvis, Supplier<String> netstat) {
        this.portaDoJarvis = portaDoJarvis;
        this.netstat = netstat;
    }

    public Relatorio vigiar() {
        if (!umPorVez.tryAcquire()) {
            throw new AcaoIndisponivelException("sentinela ocupada");
        }
        try {
            return LeitorDeNetstat.ler(netstat.get(), portaDoJarvis);
        } finally {
            umPorVez.release();
        }
    }

    static List<String> comando(String systemRoot) {
        if (!WINDOWS) {
            throw new AcaoIndisponivelException("sentinela so no windows");
        }
        Path exe = netstatDoSistema(systemRoot)
                .orElseThrow(() -> new AcaoIndisponivelException("netstat indisponivel"));
        List<String> cmd = new ArrayList<>();
        cmd.add(exe.toString());
        cmd.addAll(ARGUMENTOS);
        return cmd;
    }

    /**
     * Caminho COMPLETO do netstat do sistema. 🔒 Nunca procura pelo nome solto:
     * um netstat.exe plantado na pasta atual rodaria no lugar do verdadeiro.
     */
    static Optional<Path> netstatDoSistema(String systemRoot) {
        String raiz = systemRoot == null || systemRoot.isBlank() ? "C:\\Windows" : systemRoot;
        try {
            Path pasta = Path.of(raiz);
            if (!pasta.isAbsolute()) {
                return Optional.empty();
            }
            Path exe = pasta.resolve("System32").resolve("NETSTAT.EXE");
            return Files.isRegularFile(exe) ? Optional.of(exe) : Optional.empty();
        } catch (InvalidPathException e) {
            return Optional.empty();
        }
    }
}
