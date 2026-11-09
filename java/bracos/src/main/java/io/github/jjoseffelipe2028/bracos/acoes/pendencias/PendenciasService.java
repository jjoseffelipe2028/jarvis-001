package io.github.jjoseffelipe2028.bracos.acoes.pendencias;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.github.jjoseffelipe2028.bracos.acoes.PastaSegura;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Lê as tarefas abertas ("- [ ] ...") dos dailys dos últimos 7 dias. 🔒
 * - SÓ LÊ: nunca cria, altera ou apaga arquivo.
 * - Mesma pasta permitida do daily (vem do .env, nunca do pedido).
 * - Os nomes dos arquivos são gerados aqui (7 datas): quem pede não escolhe arquivo nenhum.
 * - Atalho (link simbólico), arquivo estranho ou nota acima de 1 MB → pulado e contado em "ignorados".
 * - Limites na resposta: até 200 itens, até 300 caracteres por item.
 */
@Service
public class PendenciasService {

    static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
    static final int DIAS = 7;
    static final int LIMITE_ITENS = 200;
    static final int LIMITE_TEXTO = 300;
    static final long LIMITE_NOTA_BYTES = 1024 * 1024;

    // "- [ ] tarefa" (também aceita * e +, e espaço antes por causa de sublista)
    private static final Pattern TAREFA_ABERTA = Pattern.compile("^\\s*[-*+]\\s+\\[ \\]\\s+(.+)$");

    // Controle, invisíveis (ex.: U+202E que inverte o texto) e surrogate solto viram espaço.
    // O U+200D fica: ele monta emojis compostos.
    private static final Pattern INVISIVEL =
            Pattern.compile("[[\\p{Cc}\\p{Cf}\\p{Cs}\\u2028\\u2029]&&[^\\u200D]]");

    /** Uma tarefa aberta e o dia (nome da nota) em que ela está. */
    public record Pendencia(String dia, String texto) {
    }

    /** total = quantas existem; pendencias = no máximo 200; ignorados = notas puladas por segurança. */
    public record Resultado(List<Pendencia> pendencias, int total, int ignorados) {
    }

    private final String pastaConfigurada;
    private final Clock relogio;

    @Autowired
    public PendenciasService(@Value("${jarvis.pastas.daily:}") String pastaConfigurada) {
        this(pastaConfigurada, Clock.system(FUSO));
    }

    /** Usado nos testes, para fixar a data. */
    PendenciasService(String pastaConfigurada, Clock relogio) {
        this.pastaConfigurada = pastaConfigurada;
        this.relogio = relogio;
    }

    public Resultado listar() {
        Path base = PastaSegura.base(pastaConfigurada);
        LocalDate hoje = LocalDate.now(relogio);

        List<Pendencia> itens = new ArrayList<>();
        int total = 0;
        int ignorados = 0;

        // Hoje primeiro, depois os dias anteriores
        for (int i = 0; i < DIAS; i++) {
            String dia = hoje.minusDays(i).toString(); // AAAA-MM-DD, gerado aqui
            Path alvo = PastaSegura.dentroDaPasta(base, dia + ".md");

            if (Files.notExists(alvo, LinkOption.NOFOLLOW_LINKS)) {
                continue; // dia sem daily: normal
            }
            String nota = lerNota(alvo);
            if (nota == null) {
                ignorados++;
                continue;
            }
            for (String linha : nota.split("\\R")) {
                Matcher m = TAREFA_ABERTA.matcher(linha);
                if (!m.matches()) {
                    continue;
                }
                String texto = limpar(m.group(1));
                if (texto.isEmpty()) {
                    continue;
                }
                total++;
                if (itens.size() < LIMITE_ITENS) {
                    itens.add(new Pendencia(dia, texto));
                }
            }
        }
        return new Resultado(List.copyOf(itens), total, ignorados);
    }

    /**
     * Lê a nota inteira, sem seguir atalho. Devolve null se ela deve ser pulada
     * (atalho, não é arquivo comum, grande demais ou erro de leitura).
     */
    private static String lerNota(Path alvo) {
        if (Files.isSymbolicLink(alvo) || !Files.isRegularFile(alvo, LinkOption.NOFOLLOW_LINKS)) {
            return null;
        }
        // Abre uma vez só e confere o tamanho no MESMO canal (sem janela entre conferir e ler)
        try (SeekableByteChannel canal = Files.newByteChannel(alvo,
                StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
            long tamanho = canal.size();
            if (tamanho > LIMITE_NOTA_BYTES) {
                return null;
            }
            // +1: se o arquivo crescer durante a leitura, percebemos e paramos no limite
            ByteBuffer bytes = ByteBuffer.allocate((int) tamanho + 1);
            while (bytes.hasRemaining() && canal.read(bytes) > 0) {
                // lê até encher ou acabar
            }
            if (bytes.position() > LIMITE_NOTA_BYTES) {
                return null;
            }
            return new String(bytes.array(), 0, bytes.position(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }

    /** Tira invisíveis e corta em 300 caracteres (sem partir emoji no meio). */
    static String limpar(String texto) {
        String limpo = INVISIVEL.matcher(texto).replaceAll(" ").strip();
        if (limpo.codePointCount(0, limpo.length()) > LIMITE_TEXTO) {
            limpo = limpo.substring(0, limpo.offsetByCodePoints(0, LIMITE_TEXTO)) + "...";
        }
        return limpo;
    }
}
