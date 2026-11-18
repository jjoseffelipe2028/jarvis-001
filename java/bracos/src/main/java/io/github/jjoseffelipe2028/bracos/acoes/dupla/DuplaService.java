package io.github.jjoseffelipe2028.bracos.acoes.dupla;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.github.jjoseffelipe2028.bracos.acoes.PastaSegura;
import io.github.jjoseffelipe2028.bracos.erros.AcaoIndisponivelException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Painel "Última palavra da dupla" (Ticket 072, parte C). 🤝🔒 Só LÊ.
 *
 * - Pasta da configuração (JARVIS_PASTA_REVIEWS). Vazia → 503. O caminho TEM que terminar em docs/reviews,
 *   senão 503: uma configuração errada não vira janela para outra pasta.
 * - Só arquivos com nome AAAA-MM-DD-slug.md, sem seguir atalho, no máximo 64 KB, dentro da pasta (PastaSegura).
 * - Da nota saem só 3 coisas: a data (do NOME), o título (1ª linha "# Registro da dupla — ", cortado em 80)
 *   e o veredito (enum fixo). O texto da nota NUNCA vai para a tela.
 * - Vale o veredito da ÚLTIMA rodada (a última linha "VEREDITO: X" ou "→ X"). Sem veredito → EM_ANALISE.
 */
@Service
public class DuplaService {

    public enum Veredito { APROVO, AJUSTES, NAO_APROVO, RESPONDIDO, EM_ANALISE }

    /** Um registro no painel: só data, título e veredito. */
    public record Registro(String data, String titulo, Veredito veredito) {
    }

    static final int MAXIMO = 5;
    static final long LIMITE_BYTES = 64 * 1024;
    static final int LIMITE_TITULO = 80;
    static final String CABECALHO = "# Registro da dupla — ";

    private static final Pattern NOME = Pattern.compile("^(\\d{4}-\\d{2}-\\d{2})-[a-z0-9][a-z0-9-]{0,79}\\.md$");
    private static final String PALAVRAS = "(APROVO|AJUSTES|N[AÃ]O[ _]APROVO|RESPONDIDO|EM[ _]AN[AÁ]LISE)";
    private static final Pattern LINHA_VEREDITO = Pattern.compile(
            "^[\\s>*_\\-]*VEREDITO\\s*:?\\s*[*_]*\\s*" + PALAVRAS + "\\b|(?:^|\\s)→\\s*[*_]*\\s*" + PALAVRAS + "\\b",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern INVISIVEL = Pattern.compile("[\\p{Cc}\\p{Cf}\\u2028\\u2029]");

    private final String pastaConfigurada;

    public DuplaService(@Value("${jarvis.pastas.reviews:}") String pastaConfigurada) {
        this.pastaConfigurada = pastaConfigurada;
    }

    public List<Registro> ultimos() {
        Path base = pastaPermitida();
        List<Path> candidatos = new ArrayList<>();
        try (DirectoryStream<Path> lista = Files.newDirectoryStream(base)) {
            for (Path p : lista) {
                if (NOME.matcher(p.getFileName().toString()).matches()) {
                    candidatos.add(p);
                }
            }
        } catch (IOException e) {
            throw new AcaoIndisponivelException("pasta de reviews ilegivel");
        }
        candidatos.sort(Comparator.comparing((Path p) -> p.getFileName().toString()).reversed());
        List<Registro> registros = new ArrayList<>();
        for (Path p : candidatos) {
            if (registros.size() >= MAXIMO) {
                break;
            }
            ler(base, p).ifPresent(registros::add);
        }
        return List.copyOf(registros);
    }

    /** Pasta vazia, que não existe ou que não termina em docs/reviews → 503 (falha fechada). */
    Path pastaPermitida() {
        Path base = PastaSegura.base(pastaConfigurada);
        int n = base.getNameCount();
        if (n < 2 || !"docs".equalsIgnoreCase(base.getName(n - 2).toString())
                || !"reviews".equalsIgnoreCase(base.getName(n - 1).toString())) {
            throw new AcaoIndisponivelException("pasta de reviews precisa terminar em docs/reviews");
        }
        return base;
    }

    static java.util.Optional<Registro> ler(Path base, Path arquivo) {
        String nome = arquivo.getFileName().toString();
        Matcher m = NOME.matcher(nome);
        if (!m.matches()) {
            return java.util.Optional.empty();
        }
        try {
            Path alvo = PastaSegura.dentroDaPasta(base, nome);
            BasicFileAttributes atr = Files.readAttributes(alvo, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            if (!atr.isRegularFile() || atr.size() > LIMITE_BYTES) {
                return java.util.Optional.empty(); // atalho, pasta ou arquivo gigante: ignora
            }
            String texto = lerTexto(alvo);
            String[] linhas = texto.split("\\R", -1);
            String primeira = linhas.length == 0 ? "" : linhas[0].replace("﻿", "");
            if (!primeira.startsWith(CABECALHO)) {
                return java.util.Optional.empty();
            }
            String titulo = INVISIVEL.matcher(primeira.substring(CABECALHO.length())).replaceAll(" ").strip();
            if (titulo.length() > LIMITE_TITULO) {
                titulo = titulo.substring(0, LIMITE_TITULO);
            }
            return java.util.Optional.of(new Registro(m.group(1), titulo, ultimoVeredito(linhas)));
        } catch (IOException | IllegalStateException e) {
            return java.util.Optional.empty(); // um arquivo ruim não derruba o painel
        }
    }

    /** O veredito da ÚLTIMA rodada: a última linha que bate. Nenhuma → EM_ANALISE. */
    static Veredito ultimoVeredito(String[] linhas) {
        Veredito achado = Veredito.EM_ANALISE;
        for (String linha : linhas) {
            Matcher m = LINHA_VEREDITO.matcher(linha);
            if (m.find()) {
                String palavra = m.group(1) != null ? m.group(1) : m.group(2);
                achado = paraEnum(palavra);
            }
        }
        return achado;
    }

    private static Veredito paraEnum(String palavra) {
        String p = palavra.toUpperCase(Locale.ROOT).replace('Ã', 'A').replace('Á', 'A').replace(' ', '_');
        return Veredito.valueOf(p);
    }

    private static String lerTexto(Path alvo) throws IOException {
        try (InputStream in = Files.newInputStream(alvo, LinkOption.NOFOLLOW_LINKS)) {
            byte[] bytes = in.readNBytes((int) LIMITE_BYTES + 1);
            if (bytes.length > LIMITE_BYTES) {
                throw new IOException("grande demais");
            }
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPLACE)
                    .onUnmappableCharacter(CodingErrorAction.REPLACE)
                    .decode(java.nio.ByteBuffer.wrap(bytes)).toString();
        }
    }
}
