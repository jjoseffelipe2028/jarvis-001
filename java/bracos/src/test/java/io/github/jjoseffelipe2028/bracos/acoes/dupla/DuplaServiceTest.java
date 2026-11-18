package io.github.jjoseffelipe2028.bracos.acoes.dupla;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import io.github.jjoseffelipe2028.bracos.acoes.dupla.DuplaService.Registro;
import io.github.jjoseffelipe2028.bracos.acoes.dupla.DuplaService.Veredito;
import io.github.jjoseffelipe2028.bracos.erros.AcaoIndisponivelException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Ticket 072 (parte C): o painel só mostra data, título curto e veredito. Nunca o texto da nota. 🤝🔒 */
class DuplaServiceTest {

    @TempDir
    Path raiz;

    Path reviews;

    @BeforeEach
    void criarPasta() throws IOException {
        reviews = Files.createDirectories(raiz.resolve("docs").resolve("reviews"));
    }

    private DuplaService servico() {
        return new DuplaService(reviews.toString());
    }

    private void nota(String nome, String texto) throws IOException {
        Files.writeString(reviews.resolve(nome), texto);
    }

    @Test
    void listaCerta_maisNovosPrimeiro_noMaximo5() throws IOException {
        for (int dia = 10; dia <= 16; dia++) {
            nota("2026-11-" + dia + "-tema.md", "# Registro da dupla — tema do dia " + dia + "\nVEREDITO: APROVO\n");
        }
        List<Registro> r = servico().ultimos();
        assertEquals(5, r.size());
        assertEquals("2026-11-16", r.get(0).data());
        assertEquals("tema do dia 16", r.get(0).titulo());
        assertEquals(Veredito.APROVO, r.get(0).veredito());
    }

    @Test
    void nomeForaDoPadrao_ePrimeiraLinhaForaDoPadrao_saoIgnorados() throws IOException {
        nota("README.md", "# Registro da dupla — não devia aparecer\n");
        nota("2026-11-15-Maiuscula.md", "# Registro da dupla — nome com maiúscula\n");
        nota("2026-11-15-revisao.md", "# Revisão de segurança — formato antigo\nVEREDITO: APROVO\n");
        nota("2026-11-15-certo.md", "# Registro da dupla — certo\n");
        List<Registro> r = servico().ultimos();
        assertEquals(1, r.size());
        assertEquals("certo", r.get(0).titulo());
    }

    @Test
    void tituloCortadoEm80_eSemCaractereInvisivel() throws IOException {
        nota("2026-11-15-longo.md", "# Registro da dupla — " + "a".repeat(200) + "‮\n");
        Registro r = servico().ultimos().get(0);
        assertEquals(80, r.titulo().length());
        assertFalse(r.titulo().contains("‮"));
    }

    @Test
    void semVeredito_EM_ANALISE_eValeOVereditoDaUltimaRodada() throws IOException {
        nota("2026-11-14-sem.md", "# Registro da dupla — sem veredito\ntexto qualquer\n");
        nota("2026-11-15-duas.md", "# Registro da dupla — duas rodadas\n## Rodada 1\nVEREDITO: AJUSTES\n"
                + "## Rodada 2\n- 002 → **NÃO APROVO**\n## Conclusão\n→ RESPONDIDO\n");
        List<Registro> r = servico().ultimos();
        assertEquals(Veredito.RESPONDIDO, r.get(0).veredito(), "vale a ÚLTIMA linha de veredito");
        assertEquals(Veredito.EM_ANALISE, r.get(1).veredito());
    }

    @Test
    void naoAprovo_comAcentoOuSublinhado() throws IOException {
        nota("2026-11-15-a.md", "# Registro da dupla — a\nVEREDITO: NÃO APROVO\n");
        nota("2026-11-16-b.md", "# Registro da dupla — b\n**VEREDITO:** NAO_APROVO\n");
        List<Registro> r = servico().ultimos();
        assertEquals(Veredito.NAO_APROVO, r.get(0).veredito());
        assertEquals(Veredito.NAO_APROVO, r.get(1).veredito());
    }

    @Test
    void textoDaNotaNuncaVaiNaResposta() throws IOException {
        nota("2026-11-15-segredo.md", "# Registro da dupla — curto\nsegredo-do-cofre JARVIS_TOKEN=abc\nVEREDITO: APROVO\n");
        String tudo = servico().ultimos().toString();
        assertFalse(tudo.contains("segredo-do-cofre"));
        assertFalse(tudo.contains("JARVIS_TOKEN"));
    }

    @Test
    void arquivoGigante_eIgnorado() throws IOException {
        nota("2026-11-15-gigante.md", "# Registro da dupla — gigante\n" + "x".repeat(70 * 1024));
        assertTrue(servico().ultimos().isEmpty());
    }

    @Test
    void atalho_eIgnorado() throws IOException {
        Path fora = Files.writeString(raiz.resolve("fora.md"), "# Registro da dupla — de fora\nVEREDITO: APROVO\n");
        try {
            Files.createSymbolicLink(reviews.resolve("2026-11-15-atalho.md"), fora);
        } catch (IOException | UnsupportedOperationException e) {
            assumeTrue(false, "este Windows não deixa criar atalho sem permissão de administrador");
        }
        assertTrue(servico().ultimos().isEmpty(), "atalho não é seguido");
    }

    @Test
    void pathTraversal_eRecusado() throws IOException {
        Path base = reviews.toRealPath();
        assertTrue(DuplaService.ler(base, base.resolve("../../fora.md")).isEmpty());
    }

    @Test
    void semPasta_ouPastaQueNaoTerminaEmDocsReviews_503() throws IOException {
        assertThrows(AcaoIndisponivelException.class, () -> new DuplaService("").ultimos());
        assertThrows(AcaoIndisponivelException.class, () -> new DuplaService(raiz.resolve("nao-existe").toString()).ultimos());
        Path errada = Files.createDirectories(raiz.resolve("Documentos"));
        assertThrows(AcaoIndisponivelException.class, () -> new DuplaService(errada.toString()).ultimos());
        Path quase = Files.createDirectories(raiz.resolve("reviews"));
        assertThrows(AcaoIndisponivelException.class, () -> new DuplaService(quase.toString()).ultimos());
    }
}
