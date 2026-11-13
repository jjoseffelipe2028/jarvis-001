package io.github.jjoseffelipe2028.bracos.seguranca;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Isca (honeytoken) do Jarvis (Ticket 034). 🪤
 * - TOKEN-ISCA: um token FALSO, parecido com o de verdade, deixado onde um curioso ou um malware acharia.
 *   Ninguém do bem usa esse token. Então qualquer pedido com ele é ALARME: alguém leu a isca e tentou entrar.
 * - ARQUIVO-ISCA: um arquivo com dados INVENTADOS (ex.: "senhas-banco.txt"). Se mudar ou sumir, é alarme.
 * - A isca nunca abre a porta: o pedido leva o mesmo 401 de qualquer token errado (o invasor não percebe a armadilha).
 * - Só CONTA e AVISA. Sem JARVIS_ISCA_TOKEN no .env, a isca fica desligada.
 * - Regra de ouro: isca só com dado FALSO. Nunca um token ou senha de verdade.
 */
@Component
public class Isca {

    static final int MAXIMO_TOQUES = 9_999;
    static final long MAXIMO_ARQUIVO = 64 * 1024;
    static final String SEM = "sem";
    static final String IGUAL = "igual";
    static final String MEXIDO = "mexido";
    static final String SUMIU = "sumiu";

    private static final DateTimeFormatter HORA =
            DateTimeFormatter.ofPattern("dd/MM HH:mm").withZone(ZoneId.of("America/Sao_Paulo"));

    /** O que a Sentinela mostra. Não tem o token, o caminho do arquivo nem nada do pedido. */
    public record Estado(boolean ligada, int toques, String ultimoToque, String arquivo) {
    }

    private final byte[] hashDaIsca;      // null = desligada
    private final Assinatura assinatura;  // confere pedido ASSINADO com o token-isca
    private final Path arquivo;           // null = sem arquivo-isca
    private final byte[] digitalDoArquivo;
    private final Clock relogio;
    private final AtomicInteger toques = new AtomicInteger();
    private final AtomicReference<Instant> ultimo = new AtomicReference<>();

    @Autowired
    public Isca(@Value("${jarvis.isca.token:}") String tokenIsca, @Value("${jarvis.isca.arquivo:}") String arquivoIsca) {
        this(tokenIsca, arquivoIsca, Clock.systemUTC());
    }

    Isca(String tokenIsca, String arquivoIsca, Clock relogio) {
        boolean desligada = tokenIsca == null || tokenIsca.isBlank();
        this.relogio = relogio;
        this.hashDaIsca = desligada ? null : sha256(tokenIsca.getBytes(StandardCharsets.UTF_8));
        this.assinatura = desligada ? null : new Assinatura(tokenIsca, relogio);
        Path caminho = arquivoIsca == null || arquivoIsca.isBlank() ? null : Path.of(arquivoIsca);
        byte[] digital = caminho == null ? null : digital(caminho);
        // Arquivo configurado, mas que não dá para ler ao ligar: fica sem vigia (não inventa alarme)
        this.arquivo = digital == null ? null : caminho;
        this.digitalDoArquivo = digital;
    }

    /** O token de verdade nunca pode ser a isca: se forem iguais, a isca não pega nada (o porteiro deixa passar antes). */
    public boolean ligada() {
        return hashDaIsca != null;
    }

    /** Token simples (cabeçalho): é a isca? Comparação de hashes em tempo constante. */
    public boolean ehOTokenIsca(String recebido) {
        return ligada() && recebido != null
                && MessageDigest.isEqual(hashDaIsca, sha256(recebido.getBytes(StandardCharsets.UTF_8)));
    }

    /** Pedido assinado: a assinatura foi feita com o token-isca? */
    public boolean assinouComAIsca(String metodo, String rota, String hora, String nonce, byte[] corpo, String assinado) {
        if (!ligada() || corpo == null) {
            return false;
        }
        return assinatura.conferir(metodo, rota, hora, nonce, corpo, assinado) == Assinatura.Resultado.OK;
    }

    /** Alguém tocou na isca: conta (com teto) e guarda a hora. */
    public void tocar() {
        toques.updateAndGet(n -> Math.min(n + 1, MAXIMO_TOQUES));
        ultimo.set(relogio.instant());
    }

    public Estado estado() {
        Instant quando = ultimo.get();
        return new Estado(ligada(), toques.get(), quando == null ? "" : HORA.format(quando), estadoDoArquivo());
    }

    String estadoDoArquivo() {
        if (arquivo == null) {
            return SEM;
        }
        if (Files.notExists(arquivo, LinkOption.NOFOLLOW_LINKS)) {
            return SUMIU;
        }
        byte[] agora = digital(arquivo);
        return agora != null && Arrays.equals(agora, digitalDoArquivo) ? IGUAL : MEXIDO;
    }

    /** SHA-256 do arquivo-isca. Atalho, pasta, arquivo grande ou erro de leitura → null. */
    private static byte[] digital(Path caminho) {
        try {
            BasicFileAttributes a = Files.readAttributes(caminho, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            if (!a.isRegularFile() || a.size() > MAXIMO_ARQUIVO) {
                return null;
            }
            try (InputStream entrada = Files.newInputStream(caminho, LinkOption.NOFOLLOW_LINKS)) {
                return sha256(entrada.readNBytes((int) MAXIMO_ARQUIVO + 1));
            }
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private static byte[] sha256(byte[] dados) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(dados);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponivel", e);
        }
    }
}
