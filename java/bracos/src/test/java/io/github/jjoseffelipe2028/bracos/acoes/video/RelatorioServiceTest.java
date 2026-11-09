package io.github.jjoseffelipe2028.bracos.acoes.video;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.List;

import io.github.jjoseffelipe2028.bracos.erros.AcaoIndisponivelException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** V2: a nota gravada no Obsidian — nome, conteúdo e ataques. */
class RelatorioServiceTest {

    // 2026-11-09 16:42:10 em Brasília
    private static final Clock RELOGIO = Clock.fixed(Instant.parse("2026-11-09T19:42:10Z"), RelatorioService.FUSO);

    @TempDir
    Path pasta;

    private final AnalisadorDeRetencao analisador = new AnalisadorDeRetencao(new PortaoDoLaboratorio());

    private String gravar(RelatorioService servico, String titulo) {
        RelatorioRequest p = new RelatorioRequest(titulo, "facebook", 190, 55.5, 80, 4.25, List.of(55));
        return servico.gravar(p, analisador.analisar(p));
    }

    @Test
    void criaNotaComNomeDoServidor() throws IOException {
        String nome = gravar(new RelatorioService(pasta.toString(), RELOGIO), "Receita de bolo");
        assertEquals("2026-11-09-164210-retencao.md", nome);

        String nota = Files.readString(pasta.resolve(nome), StandardCharsets.UTF_8);
        assertTrue(nota.startsWith("# 🎬 Retenção: Receita de bolo\n"));
        assertTrue(nota.contains("| Duração | 3:10 |"));
        assertTrue(nota.contains("| 3s→1min | 55.5% |"));
        assertTrue(nota.contains("| CTR | 4.3% |") || nota.contains("| CTR | 4.2% |"));
        assertTrue(nota.contains("| Quedas | 0:55 |"));
        assertTrue(nota.contains("Dublagem: ✅ liberada"));
        assertTrue(nota.contains("queda aos 0:55, logo antes do anúncio de 1:00 → colocar uma promessa"));
    }

    @Test
    void mesmoSegundo_naoSobrescreve_ganhaSufixo() throws IOException {
        RelatorioService s = new RelatorioService(pasta.toString(), RELOGIO);
        String primeiro = gravar(s, "Um");
        String segundo = gravar(s, "Dois");
        assertEquals("2026-11-09-164210-2-retencao.md", segundo);
        assertTrue(Files.readString(pasta.resolve(primeiro), StandardCharsets.UTF_8).contains("Um"));
    }

    @Test
    void tituloComCaminho_naoViraNomeDeArquivo() throws IOException {
        String nome = gravar(new RelatorioService(pasta.toString(), RELOGIO), "../../Windows/evil");
        assertEquals("2026-11-09-164210-retencao.md", nome);
        try (var arquivos = Files.list(pasta)) {
            assertEquals(1, arquivos.count(), "só a nota nova, dentro da pasta");
        }
    }

    @Test
    void tituloComMarkdownAtivo_saiDesarmado() throws IOException {
        String nome = gravar(new RelatorioService(pasta.toString(), RELOGIO),
                "![x](http://evil/p.png) <script>\n## falso\n```dataviewjs");
        String nota = Files.readString(pasta.resolve(nome), StandardCharsets.UTF_8);
        String primeiraLinha = nota.lines().findFirst().orElseThrow();
        assertTrue(primeiraLinha.contains("\\!\\[x\\]"), "imagem desarmada");
        assertTrue(primeiraLinha.contains("\\<script\\>"), "HTML desarmado");
        assertTrue(primeiraLinha.contains("\\`\\`\\`"), "bloco de código desarmado");
        assertFalse(nota.contains("\n## falso"), "quebra de linha não cria título falso");
    }

    @Test
    void semPastaConfigurada_503() {
        RelatorioService s = new RelatorioService("", RELOGIO);
        assertThrows(AcaoIndisponivelException.class, () -> gravar(s, "x"));
    }

    @Test
    void pastaQueNaoExiste_503() {
        RelatorioService s = new RelatorioService(pasta.resolve("nao-existe").toString(), RELOGIO);
        assertThrows(AcaoIndisponivelException.class, () -> gravar(s, "x"));
    }
}
