package io.github.jjoseffelipe2028.bracos.central;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Chave de USO ÚNICO que liga o ícone do Jarvis à Central (Ticket 015). 🔒
 * - Quando a API liga, nasce uma chave aleatória (256 bits) num arquivo do PC (fora do Git).
 * - O lançador (Jarvis-Central.bat) lê o arquivo e entrega a chave para a página.
 * - A página troca a chave por um "crachá" (cookie HttpOnly) e a chave é TROCADA na hora:
 *   quem copiar a chave depois (ex.: do histórico do navegador) não consegue usar.
 * - Sem arquivo configurado (JARVIS_CHAVE_CENTRAL vazio) → Central sem botões (falha fechada).
 */
@Component
public class ChaveDaCentral {

    private static final SecureRandom SORTEIO = new SecureRandom();
    private static final Set<OpenOption> GRAVAR = Set.of(StandardOpenOption.CREATE, StandardOpenOption.WRITE,
            StandardOpenOption.TRUNCATE_EXISTING, LinkOption.NOFOLLOW_LINKS);

    private final Path arquivo; // null = desligada
    private byte[] hashAtual;

    public ChaveDaCentral(@Value("${jarvis.central.arquivo-chave:}") String arquivoConfigurado) {
        this.arquivo = arquivoConfigurado == null || arquivoConfigurado.isBlank()
                ? null : Path.of(arquivoConfigurado).toAbsolutePath().normalize();
        if (arquivo != null) {
            trocar();
        }
    }

    public boolean ligada() {
        return arquivo != null;
    }

    /**
     * Confere a chave (tempo constante) e, se bater, já troca por outra.
     * @return true só na PRIMEIRA vez que a chave certa chega.
     */
    public synchronized boolean usar(String recebida) {
        if (arquivo == null || recebida == null || recebida.length() > 128) {
            return false;
        }
        boolean ok = MessageDigest.isEqual(hashAtual, sha256(recebida));
        if (ok) {
            trocar();
        }
        return ok;
    }

    private synchronized void trocar() {
        byte[] bytes = new byte[32];
        SORTEIO.nextBytes(bytes);
        String nova = HexFormat.of().formatHex(bytes);
        if (Files.isSymbolicLink(arquivo)) {
            throw new IllegalStateException("arquivo da chave e um atalho"); // poderia apontar para outro lugar
        }
        try (SeekableByteChannel canal = Files.newByteChannel(arquivo, GRAVAR)) {
            ByteBuffer dados = ByteBuffer.wrap(nova.getBytes(StandardCharsets.US_ASCII));
            while (dados.hasRemaining()) {
                canal.write(dados);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("falha ao gravar a chave da central", e);
        }
        hashAtual = sha256(nova); // na memória fica só o hash, nunca a chave
    }

    static byte[] sha256(String texto) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(texto.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponivel", e);
        }
    }
}
