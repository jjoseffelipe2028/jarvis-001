package io.github.jjoseffelipe2028.bracos.acoes.pedidos;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

import io.github.jjoseffelipe2028.bracos.acoes.PastaSegura;
import io.github.jjoseffelipe2028.bracos.acoes.TextoSeguro;
import io.github.jjoseffelipe2028.bracos.erros.EntradaInvalidaException;
import io.github.jjoseffelipe2028.bracos.erros.LimiteAtingidoException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Caixa de pedidos para a 001 (Ticket 060). 📬🔒
 * - O Jarvis só ANOTA o pedido numa nota nova em pedidos/. Quem faz é a 001, com o OK do José.
 * - A pasta vem da configuração (JARVIS_PASTA_PEDIDOS), nunca do pedido.
 * - O nome do arquivo é gerado aqui (AAAA-MM-DD-HHmmss.md, hora de Brasília), nunca do pedido.
 * - Arquivo sempre NOVO (CREATE_NEW, sem seguir atalho): nunca sobrescreve nada.
 * - Texto "desarmado" (TextoSeguro), igual ao daily.
 * - Anti-enxurrada: no máximo {@value #LIMITE_POR_DIA} pedidos por dia.
 */
@Service
public class PedidoService {

    static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
    static final int LIMITE_POR_DIA = 30;

    private static final DateTimeFormatter NOME = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss");
    private static final DateTimeFormatter DIA = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter CRIADO = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final Set<OpenOption> CRIAR_NOVO =
            Set.of(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);

    private final String pastaConfigurada;
    private final Clock relogio;
    private final ReentrantLock trava = new ReentrantLock();

    @Autowired
    public PedidoService(@Value("${jarvis.pastas.pedidos:}") String pastaConfigurada) {
        this(pastaConfigurada, Clock.system(FUSO));
    }

    /** Usado nos testes, para fixar a data e a hora. */
    PedidoService(String pastaConfigurada, Clock relogio) {
        this.pastaConfigurada = pastaConfigurada;
        this.relogio = relogio;
    }

    /**
     * Grava o pedido numa nota nova.
     * @return só o NOME do arquivo (ex.: 2026-11-15-143200.md), nunca o caminho completo.
     */
    public String anotar(String texto) {
        String limpo = TextoSeguro.desarmar(texto);
        if (limpo.isEmpty()) {
            throw new EntradaInvalidaException("texto vazio");
        }
        LocalDateTime agora = LocalDateTime.now(relogio);
        String conteudo = "---\nstatus: aberto\norigem: central\ncriado: " + agora.format(CRIADO) + "\n---\n"
                + "# Pedido para a 001\n" + limpo + "\n";
        Path base = PastaSegura.base(pastaConfigurada);

        trava.lock();
        try {
            if (pedidosDoDia(base, agora.format(DIA)) >= LIMITE_POR_DIA) {
                throw new LimiteAtingidoException("limite de pedidos do dia");
            }
            String prefixo = agora.format(NOME);
            for (int n = 1; n <= 9; n++) {
                String nome = n == 1 ? prefixo + ".md" : prefixo + "-" + n + ".md";
                Path alvo = PastaSegura.dentroDaPasta(base, nome);
                try (SeekableByteChannel canal = Files.newByteChannel(alvo, CRIAR_NOVO)) {
                    ByteBuffer bytes = ByteBuffer.wrap(conteudo.getBytes(StandardCharsets.UTF_8));
                    while (bytes.hasRemaining()) {
                        canal.write(bytes);
                    }
                    return nome;
                } catch (FileAlreadyExistsException e) {
                    // mesmo segundo: tenta o próximo sufixo (-2, -3...)
                }
            }
            throw new LimiteAtingidoException("pedidos demais no mesmo segundo");
        } catch (IOException e) {
            throw new UncheckedIOException("falha ao gravar o pedido", e);
        } finally {
            trava.unlock();
        }
    }

    /** Quantos pedidos já existem hoje (arquivos AAAA-MM-DD-*.md). */
    private static int pedidosDoDia(Path base, String dia) throws IOException {
        int total = 0;
        try (DirectoryStream<Path> arquivos = Files.newDirectoryStream(base, dia + "-*.md")) {
            for (Path ignorado : arquivos) {
                total++;
            }
        }
        return total;
    }
}
