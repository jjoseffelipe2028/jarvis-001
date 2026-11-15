package io.github.jjoseffelipe2028.bracos.acoes.ronda;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.github.jjoseffelipe2028.bracos.acoes.estudio.EstudioService;
import io.github.jjoseffelipe2028.bracos.acoes.estudio.Processos;
import io.github.jjoseffelipe2028.bracos.erros.AcaoIndisponivelException;
import io.github.jjoseffelipe2028.bracos.erros.LimiteAtingidoException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Ronda do defensor pela Central (Ticket 061). 🛡️🔒
 * - Roda UM comando fixo: {@code <python da venv> defensor.py}, na pasta python/. Nada vem da tela.
 * - Sem shell, entrada vazia, tempo máximo e saída limitada (o mesmo {@link Processos} do Estúdio).
 * - Uma ronda por vez: a segunda chamada recebe 429.
 * - A saída só vale no formato "ícone vigia: frase", com o vigia da LISTA FECHADA. O resto é ignorado.
 * - Frases limpas: sem caractere de controle, sem caminho do PC, no máximo 300 letras.
 * - O defensor.py sai com código 1 quando tem ALERTA: isso é resultado (🚨), não erro.
 */
@Service
public class RondaService {

    static final Duration LIMITE = Duration.ofSeconds(90);
    static final List<String> VIGIAS = List.of("Sentinela", "Processos", "Rede", "Backup", "Duelo");
    static final Map<String, String> NIVEL_DO_ICONE = Map.of("✅", "ok", "⚠️", "atencao", "⚠", "atencao", "🚨", "alerta", "❔", "falha");
    private static final Map<String, Integer> PESO = Map.of("ok", 0, "falha", 1, "atencao", 2, "alerta", 3);

    private static final Pattern LINHA_VIGIA = Pattern.compile("^(✅|⚠️|⚠|🚨|❔)\\s+(\\p{L}+):\\s+(.+)$");
    private static final Pattern LINHA_GERAL = Pattern.compile("^Defensor:\\s+.+$");
    private static final Pattern INVISIVEL = Pattern.compile("[\\p{Cc}\\p{Cf}\\u2028\\u2029&&[^\\u200D]]");
    // C:\Users\..., C:/..., \\servidor\... e /home/... viram "…": o caminho do PC não vai para a tela
    private static final Pattern CAMINHO = Pattern.compile("(?i)(?:[a-z]:[\\\\/]|\\\\\\\\|/(?:home|users|mnt)/)\\S*");

    /** Um vigia na tela: nome da lista fechada, nível e a frase limpa. */
    public record Vigia(String vigia, String nivel, String frase) {
    }

    /** O resultado da ronda. {@code geral} = o pior nível (um alerta não some atrás de "tudo certo"). */
    public record Resultado(String geral, String frase, List<Vigia> vigias) {
    }

    private final Path python;
    private final Path pastaPython;
    private final EstudioService.Executor executor;
    private final AtomicBoolean ocupado = new AtomicBoolean(false);

    @Autowired
    public RondaService(@Value("${jarvis.cortes.python:}") String python) {
        this(python, RondaService::rodarDeVerdade);
    }

    RondaService(String python, EstudioService.Executor executor) {
        Path pastaDoPython = Path.of(System.getProperty("user.dir")).resolve("../../python").toAbsolutePath().normalize();
        this.python = python == null || python.isBlank()
                ? pastaDoPython.resolve(".venv").resolve("Scripts").resolve("python.exe")
                : Path.of(python).toAbsolutePath().normalize();
        // .venv/Scripts/python.exe → a pasta python/ fica 3 níveis acima
        Path tresAcima = this.python.getParent() == null || this.python.getParent().getParent() == null
                ? null : this.python.getParent().getParent().getParent();
        this.pastaPython = tresAcima != null ? tresAcima : pastaDoPython;
        this.executor = executor;
    }

    public Resultado fazerRonda() {
        if (!ocupado.compareAndSet(false, true)) {
            throw new LimiteAtingidoException("ja tem uma ronda rodando");
        }
        try {
            String saida;
            try {
                saida = executor.rodar(List.of(python.toString(), "defensor.py"), pastaPython, LIMITE);
            } catch (Processos.SaidaComErro e) {
                saida = e.saida(); // código 1 = tem ALERTA: a saída continua valendo
            } catch (IOException e) {
                throw new AcaoIndisponivelException("ronda indisponivel");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AcaoIndisponivelException("ronda interrompida");
            }
            Resultado r = ler(saida);
            if (r.vigias().isEmpty()) {
                throw new AcaoIndisponivelException("ronda sem resultado");
            }
            return r;
        } finally {
            ocupado.set(false);
        }
    }

    /** Lê a saída do defensor.py. Só aceita o formato combinado e os vigias da lista fechada. */
    static Resultado ler(String saida) {
        List<Vigia> vigias = new ArrayList<>();
        String frase = "";
        for (String bruta : (saida == null ? "" : saida).split("\\R")) {
            String linha = bruta.strip();
            Matcher m = LINHA_VIGIA.matcher(linha);
            if (m.matches() && VIGIAS.contains(m.group(2)) && vigias.stream().noneMatch(v -> v.vigia().equals(m.group(2)))) {
                vigias.add(new Vigia(m.group(2), NIVEL_DO_ICONE.get(m.group(1)), limpar(m.group(3))));
            } else if (LINHA_GERAL.matcher(linha).matches()) {
                frase = limpar(linha);
            }
        }
        String geral = vigias.stream().map(Vigia::nivel).max((a, b) -> PESO.get(a) - PESO.get(b)).orElse("falha");
        return new Resultado(geral, frase, List.copyOf(vigias));
    }

    static String limpar(String texto) {
        String semInvisivel = INVISIVEL.matcher(texto).replaceAll(" ");
        String semCaminho = CAMINHO.matcher(semInvisivel).replaceAll("…");
        String junto = String.join(" ", semCaminho.strip().split("\\s+"));
        return junto.length() > 300 ? junto.substring(0, 300) : junto;
    }

    private static String rodarDeVerdade(List<String> comando, Path pasta, Duration limite) throws IOException, InterruptedException {
        // semSegredos = false: o defensor.py é NOSSO código e precisa das pastas e do token para perguntar à Sentinela
        return Processos.rodar(comando, pasta, limite, false, 64 * 1024, true);
    }
}
