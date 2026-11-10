package io.github.jjoseffelipe2028.bracos.acoes.sentinela;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.github.jjoseffelipe2028.bracos.erros.AcaoIndisponivelException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Sentinela v1: vigia de integridade, o "FIM" (File Integrity Monitoring) do Jarvis (Ticket 022). 🔏
 * - Quando a API liga, guarda a impressão digital (SHA-256) das TRAVAS do projeto.
 * - A cada pedido, calcula de novo e diz o que mudou desde então. Só AVISA: não conserta nada.
 * - Lista FIXA de arquivos (nada vem do pedido). Na resposta, só o nome curto e 12 letras do hash.
 * - Não segue atalho (symlink) e não lê arquivo acima de 1 MB.
 * Limite da v1: a "foto" fica na memória. Mudança feita com a API desligada não aparece.
 */
@Service
public class IntegridadeService {

    /** As travas: regras da IA, permissões, o hook guarda, a config de rede da API e o .gitignore. */
    static final List<String> TRAVAS = List.of(
            "CLAUDE.md",
            ".claude/settings.json",
            ".claude/hooks/guarda.mjs",
            "java/bracos/src/main/resources/application.properties",
            ".gitignore");

    static final long LIMITE_BYTES = 1024 * 1024;
    static final String IGUAL = "igual";
    static final String ALTERADO = "alterado";
    static final String SUMIU = "sumiu";
    static final String APARECEU = "apareceu";
    static final String ESTRANHO = "estranho"; // atalho, pasta no lugar do arquivo ou grande demais

    private static final String AUSENTE = "-";
    private static final String INVALIDO = "!";
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

    public record Arquivo(String nome, String estado, String hash) {
    }

    public record Relatorio(int vigiados, int alterados, String desde, List<Arquivo> arquivos) {
    }

    private final Path raiz; // null = projeto não encontrado (ação desligada)
    private final Map<String, String> foto;
    private final String desde;

    @Autowired
    public IntegridadeService(@Value("${jarvis.sentinela.projeto:}") String projetoConfigurado) {
        this(acharProjeto(projetoConfigurado), Clock.system(ZoneId.of("America/Sao_Paulo")));
    }

    /** Usado nos testes: pasta do projeto e relógio fixos. */
    IntegridadeService(Path raiz, Clock relogio) {
        this.raiz = raiz;
        this.foto = raiz == null ? Map.of() : fotografar(raiz);
        this.desde = LocalTime.now(relogio).format(HORA);
    }

    public Relatorio conferir() {
        if (raiz == null) {
            throw new AcaoIndisponivelException("projeto nao encontrado");
        }
        Map<String, String> agora = fotografar(raiz);
        List<Arquivo> arquivos = new ArrayList<>();
        int alterados = 0;
        for (String nome : TRAVAS) {
            String antes = foto.get(nome);
            String depois = agora.get(nome);
            String estado = estado(antes, depois);
            if (!IGUAL.equals(estado)) {
                alterados++;
            }
            String curto = depois.length() >= 12 ? depois.substring(0, 12) : depois;
            arquivos.add(new Arquivo(nome, estado, curto));
        }
        return new Relatorio(TRAVAS.size(), alterados, desde, List.copyOf(arquivos));
    }

    static String estado(String antes, String depois) {
        if (INVALIDO.equals(depois)) {
            return ESTRANHO;
        }
        if (antes.equals(depois)) {
            return IGUAL;
        }
        if (AUSENTE.equals(depois)) {
            return SUMIU;
        }
        if (AUSENTE.equals(antes)) {
            return APARECEU;
        }
        return ALTERADO;
    }

    static Map<String, String> fotografar(Path raiz) {
        Map<String, String> mapa = new LinkedHashMap<>();
        for (String nome : TRAVAS) {
            mapa.put(nome, impressao(raiz.resolve(nome)));
        }
        return mapa;
    }

    /** SHA-256 do arquivo; "-" se não existe; "!" se é atalho, pasta ou grande demais. */
    static String impressao(Path arquivo) {
        try {
            if (Files.isSymbolicLink(arquivo)) {
                return INVALIDO;
            }
            if (!Files.exists(arquivo, LinkOption.NOFOLLOW_LINKS)) {
                return AUSENTE;
            }
            if (!Files.isRegularFile(arquivo, LinkOption.NOFOLLOW_LINKS) || Files.size(arquivo) > LIMITE_BYTES) {
                return INVALIDO;
            }
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            try (InputStream in = Files.newInputStream(arquivo, LinkOption.NOFOLLOW_LINKS)) {
                byte[] bloco = new byte[8192];
                long lidos = 0;
                for (int n = in.read(bloco); n > 0; n = in.read(bloco)) {
                    lidos += n;
                    if (lidos > LIMITE_BYTES) {
                        return INVALIDO; // cresceu enquanto lia
                    }
                    sha.update(bloco, 0, n);
                }
            }
            return HexFormat.of().formatHex(sha.digest());
        } catch (IOException e) {
            return INVALIDO;
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 faz parte de todo Java", e);
        }
    }

    /**
     * Pasta do projeto: a configurada (JARVIS_PASTA_PROJETO) ou, sem config, duas pastas acima de onde
     * a API roda (java/bracos → raiz). Só vale se tiver CLAUDE.md e .git: senão a ação fica desligada.
     */
    static Path acharProjeto(String configurado) {
        try {
            Path base = configurado == null || configurado.isBlank()
                    ? Path.of(System.getProperty("user.dir")).resolve("../..")
                    : Path.of(configurado);
            Path raiz = base.toAbsolutePath().normalize();
            boolean pareceProjeto = Files.isRegularFile(raiz.resolve("CLAUDE.md"))
                    && Files.exists(raiz.resolve(".git"));
            return pareceProjeto ? raiz : null;
        } catch (InvalidPathException e) {
            return null;
        }
    }
}
