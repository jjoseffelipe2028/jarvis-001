package io.github.jjoseffelipe2028.bracos.acoes.daily;

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
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

import io.github.jjoseffelipe2028.bracos.acoes.PastaSegura;
import io.github.jjoseffelipe2028.bracos.acoes.TextoSeguro;
import io.github.jjoseffelipe2028.bracos.erros.AcaoIndisponivelException;
import io.github.jjoseffelipe2028.bracos.erros.EntradaInvalidaException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Escreve uma linha no daily de hoje do Obsidian. 🔒
 * - A pasta vem da configuração (JARVIS_PASTA_DAILY no .env), nunca do pedido.
 * - O nome do arquivo é gerado aqui (AAAA-MM-DD.md, data de Brasília), nunca do pedido.
 * - O texto é "desarmado": sem quebra de linha, sem caractere invisível e sem marcação
 *   Markdown/HTML ativa (imagem, link, código, Dataview, Templater).
 * - Só acrescenta no fim; nunca apaga nem sobrescreve. Nota acima de 1 MB → recusa.
 * - Uma trava (lock) impede que duas escritas ao mesmo tempo misturem as linhas.
 */
@Service
public class DailyService {

    static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");

    /** Tamanho máximo da nota: protege o disco, o OneDrive e o Obsidian. */
    static final long LIMITE_NOTA_BYTES = 1024 * 1024;

    private static final DateTimeFormatter FORMATO_HORA = DateTimeFormatter.ofPattern("HH:mm");

    private static final Set<OpenOption> ABRIR_EXISTENTE =
            Set.of(StandardOpenOption.READ, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);
    private static final Set<OpenOption> CRIAR_NOVO =
            Set.of(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);

    private final String pastaConfigurada;
    private final Clock relogio;
    private final ReentrantLock trava = new ReentrantLock();

    @Autowired
    public DailyService(@Value("${jarvis.pastas.daily:}") String pastaConfigurada) {
        this(pastaConfigurada, Clock.system(FUSO));
    }

    /** Usado nos testes, para fixar a data e a hora. */
    DailyService(String pastaConfigurada, Clock relogio) {
        this.pastaConfigurada = pastaConfigurada;
        this.relogio = relogio;
    }

    /**
     * Acrescenta "- HH:mm texto" no fim da nota de hoje.
     * @return só o NOME do arquivo (ex.: 2026-11-08.md), nunca o caminho completo.
     */
    public String registrar(String texto) {
        String limpo = desarmar(texto);
        if (limpo.isEmpty()) {
            throw new EntradaInvalidaException("texto vazio");
        }

        LocalDate hoje = LocalDate.now(relogio);
        String nomeArquivo = hoje + ".md"; // LocalDate.toString() = AAAA-MM-DD
        String linha = "- " + LocalTime.now(relogio).format(FORMATO_HORA) + " " + limpo + "\n";

        Path base = pastaPermitida();
        Path alvo = dentroDaPasta(base, nomeArquivo);

        trava.lock();
        try {
            if (Files.isSymbolicLink(alvo)) {
                // Um atalho poderia apontar para fora da pasta: recusa
                throw new IllegalStateException("arquivo e um link simbolico");
            }
            boolean novo = Files.notExists(alvo, LinkOption.NOFOLLOW_LINKS);
            // Abre UMA vez só (sem seguir atalho) e usa o mesmo canal para conferir e escrever:
            // fecha a janela entre "conferir" e "usar" (TOCTOU)
            try (SeekableByteChannel canal = Files.newByteChannel(alvo, novo ? CRIAR_NOVO : ABRIR_EXISTENTE)) {
                StringBuilder saida = new StringBuilder();
                if (novo) {
                    saida.append("# ").append(hoje).append("\n\n");
                } else {
                    long tamanho = canal.size();
                    if (tamanho >= LIMITE_NOTA_BYTES) {
                        throw new AcaoIndisponivelException("daily cheio");
                    }
                    if (!terminaComQuebraDeLinha(canal, tamanho)) {
                        // Se a nota não terminar com "enter", a linha nova não pode grudar na última
                        saida.append('\n');
                    }
                    canal.position(tamanho); // sempre no fim: nunca sobrescreve
                }
                saida.append(linha);
                ByteBuffer bytes = ByteBuffer.wrap(saida.toString().getBytes(StandardCharsets.UTF_8));
                while (bytes.hasRemaining()) {
                    canal.write(bytes);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("falha ao gravar no daily", e);
        } finally {
            trava.unlock();
        }
        return nomeArquivo;
    }

    /**
     * Desarma o texto antes de ir para a nota:
     * 1) invisíveis e quebras de linha viram espaço (anti injeção de linhas e "Trojan Source");
     * 2) marcação ganha "\" na frente: o Obsidian mostra o caractere, mas não executa nada.
     */
    static String desarmar(String texto) {
        return TextoSeguro.desarmar(texto); // regra compartilhada (Ticket 013)
    }

    /** Garante que o arquivo fica DENTRO da pasta permitida (anti Path Traversal). */
    static Path dentroDaPasta(Path base, String nomeArquivo) {
        return PastaSegura.dentroDaPasta(base, nomeArquivo);
    }

    /** Pasta vazia ou que não existe → 503 (falha fechada: na dúvida, nega). */
    private Path pastaPermitida() {
        return PastaSegura.base(pastaConfigurada);
    }

    private static boolean terminaComQuebraDeLinha(SeekableByteChannel canal, long tamanho) throws IOException {
        if (tamanho == 0) {
            return true;
        }
        ByteBuffer ultimo = ByteBuffer.allocate(1);
        canal.position(tamanho - 1);
        canal.read(ultimo);
        return ultimo.get(0) == '\n';
    }
}
