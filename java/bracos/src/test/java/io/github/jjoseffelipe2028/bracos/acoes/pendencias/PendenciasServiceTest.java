package io.github.jjoseffelipe2028.bracos.acoes.pendencias;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Clock;
import java.time.ZonedDateTime;
import java.util.List;

import io.github.jjoseffelipe2028.bracos.acoes.pendencias.PendenciasService.Pendencia;
import io.github.jjoseffelipe2028.bracos.acoes.pendencias.PendenciasService.Resultado;
import io.github.jjoseffelipe2028.bracos.erros.AcaoIndisponivelException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Testa o Service direto, numa pasta temporária (nunca no Obsidian de verdade). */
class PendenciasServiceTest {

    // Relógio fixo: 08/11/2026 em Brasília
    private static final Clock RELOGIO = Clock.fixed(
            ZonedDateTime.of(2026, 11, 8, 23, 50, 0, 0, PendenciasService.FUSO).toInstant(), PendenciasService.FUSO);

    @TempDir
    Path pasta;

    private Resultado listar() {
        return new PendenciasService(pasta.toString(), RELOGIO).listar();
    }

    private void nota(String nome, String conteudo) throws Exception {
        Files.writeString(pasta.resolve(nome), conteudo, StandardCharsets.UTF_8);
    }

    @Test
    void pegaSoTarefasAbertas() throws Exception {
        nota("2026-11-08.md", "# dia\n\n- [ ] estudar Git\n- [x] feito\ntexto normal\n- item comum\n- [ ] prova Cyber\n");

        Resultado r = listar();

        assertEquals(List.of(new Pendencia("2026-11-08", "estudar Git"),
                new Pendencia("2026-11-08", "prova Cyber")), r.pendencias());
        assertEquals(2, r.total());
        assertEquals(0, r.ignorados());
    }

    @Test
    void ultimos7Dias_hojePrimeiro_e8oDiaFicaDeFora() throws Exception {
        nota("2026-11-08.md", "- [ ] hoje\n");
        nota("2026-11-02.md", "- [ ] seis dias atras\n");
        nota("2026-11-01.md", "- [ ] sete dias atras (fora)\n");

        assertEquals(List.of(new Pendencia("2026-11-08", "hoje"),
                new Pendencia("2026-11-02", "seis dias atras")), listar().pendencias());
    }

    @Test
    void semNenhumDaily_listaVazia() {
        Resultado r = listar();
        assertEquals(List.of(), r.pendencias());
        assertEquals(0, r.total());
    }

    @Test
    void aceitaAsteriscoMaisSublistaECrlf() throws Exception {
        nota("2026-11-08.md", "* [ ] asterisco\r\n+ [ ] mais\r\n    - [ ] sublista\r\n");

        assertEquals(3, listar().total());
    }

    @Test
    void tarefaVaziaEIgnorada() throws Exception {
        nota("2026-11-08.md", "- [ ]    \n- [ ] ‮\n- [ ] ok\n");

        assertEquals(List.of(new Pendencia("2026-11-08", "ok")), listar().pendencias());
    }

    @Test
    void naoLeOutrosArquivosDaPasta() throws Exception {
        nota("segredo.md", "- [ ] nao pode aparecer\n");
        nota("2026-11-08.txt", "- [ ] nem este\n");

        assertEquals(0, listar().total());
    }

    @Test
    void notaGrandeDemaisEPulada() throws Exception {
        nota("2026-11-08.md", "- [ ] x\n" + "a".repeat((int) PendenciasService.LIMITE_NOTA_BYTES));
        nota("2026-11-07.md", "- [ ] ontem\n");

        Resultado r = listar();
        assertEquals(List.of(new Pendencia("2026-11-07", "ontem")), r.pendencias());
        assertEquals(1, r.ignorados());
    }

    @Test
    void pastaComNomeDeDailyEPulada() throws Exception {
        Files.createDirectory(pasta.resolve("2026-11-08.md"));

        assertEquals(1, listar().ignorados());
    }

    @Test
    void linkSimbolicoEPulado() throws Exception {
        Path fora = Files.createTempFile("fora-da-pasta", ".md");
        Files.writeString(fora, "- [ ] segredo de fora\n");
        Path link = pasta.resolve("2026-11-08.md");
        try {
            Files.createSymbolicLink(link, fora);
        } catch (Exception e) {
            // No Windows, criar link simbólico exige modo desenvolvedor; no GitHub Actions (Linux) roda
            org.junit.jupiter.api.Assumptions.abort("sistema nao deixa criar link simbolico");
        }
        try {
            Resultado r = listar();
            assertEquals(0, r.total(), "não pode ler arquivo fora da pasta pelo atalho");
            assertEquals(1, r.ignorados());
        } finally {
            Files.deleteIfExists(link);
            Files.deleteIfExists(fora);
        }
    }

    @Test
    void limiteDe200Itens_totalMostraOReal() throws Exception {
        nota("2026-11-08.md", "- [ ] tarefa\n".repeat(250));

        Resultado r = listar();
        assertEquals(PendenciasService.LIMITE_ITENS, r.pendencias().size());
        assertEquals(250, r.total());
    }

    @Test
    void textoLongoECortadoSemPartirEmoji() throws Exception {
        nota("2026-11-08.md", "- [ ] " + "💪".repeat(400) + "\n");

        String texto = listar().pendencias().get(0).texto();
        assertEquals(PendenciasService.LIMITE_TEXTO, texto.codePointCount(0, texto.length() - 3));
        assertTrue(texto.endsWith("💪..."));
    }

    @Test
    void invisivelViraEspaco() throws Exception {
        nota("2026-11-08.md", "- [ ] abc‮def\u0007g\n");

        assertEquals("abc def g", listar().pendencias().get(0).texto());
    }

    /** Ticket 008: letras "em branco" e acentos empilhados (zalgo) não passam. */
    @Test
    void letraEmBrancoEZalgo_saemLimpos() throws Exception {
        nota("2026-11-08.md", "- [ ] ㅤ⠀\n- [ ] á́́́́b\n");

        List<Pendencia> itens = listar().pendencias();
        assertEquals(1, itens.size(), "a tarefa só de letras em branco não conta");
        assertEquals("á́b", itens.get(0).texto());
    }

    @Test
    void acentoEUtf8() throws Exception {
        nota("2026-11-08.md", "- [ ] revisão de ação — café ☕\n");

        assertEquals("revisão de ação — café ☕", listar().pendencias().get(0).texto());
    }

    @Test
    void soLe_naoAlteraANota() throws Exception {
        nota("2026-11-08.md", "- [ ] nao mexer\n");
        Path arquivo = pasta.resolve("2026-11-08.md");
        Files.setLastModifiedTime(arquivo, FileTime.fromMillis(1_000_000_000_000L));
        byte[] antes = Files.readAllBytes(arquivo);

        listar();

        assertArrayEquals(antes, Files.readAllBytes(arquivo));
        assertEquals(FileTime.fromMillis(1_000_000_000_000L), Files.getLastModifiedTime(arquivo));
        try (var arquivos = Files.list(pasta)) {
            assertEquals(1, arquivos.count(), "não pode criar arquivo novo");
        }
    }

    @Test
    void pastaNaoConfiguradaOuInexistente_503() {
        assertThrows(AcaoIndisponivelException.class, () -> new PendenciasService("", RELOGIO).listar());
        assertThrows(AcaoIndisponivelException.class,
                () -> new PendenciasService(pasta.resolve("nao-existe").toString(), RELOGIO).listar());
    }
}
