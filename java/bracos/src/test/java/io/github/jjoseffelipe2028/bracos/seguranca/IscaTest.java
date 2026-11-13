package io.github.jjoseffelipe2028.bracos.seguranca;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Ticket 034: a isca (honeytoken) sozinha. Tokens e "senhas" daqui são INVENTADOS. 🪤 */
class IscaTest {

    private static final Clock RELOGIO = Clock.fixed(Instant.parse("2026-11-12T23:30:00Z"), ZoneOffset.UTC);
    private static final String ISCA = "isca-falsa-de-teste";

    @TempDir
    Path pasta;

    @Test
    void semTokenIsca_ficaDesligada_eNadaEIsca() {
        for (String vazio : new String[] {null, "", "   "}) {
            Isca isca = new Isca(vazio, "", RELOGIO);
            assertFalse(isca.ligada());
            assertFalse(isca.ehOTokenIsca(""));
            assertFalse(isca.ehOTokenIsca(ISCA));
            assertFalse(isca.assinouComAIsca("GET", "/x", "1", "a", new byte[0], "b"));
            assertEquals(new Isca.Estado(false, 0, "", Isca.SEM), isca.estado());
        }
    }

    @Test
    void soOTokenIscaExato_eIsca() {
        Isca isca = new Isca(ISCA, "", RELOGIO);
        assertTrue(isca.ligada());
        assertTrue(isca.ehOTokenIsca(ISCA));
        assertFalse(isca.ehOTokenIsca(ISCA + "x"));
        assertFalse(isca.ehOTokenIsca(ISCA.toUpperCase()));
        assertFalse(isca.ehOTokenIsca(null));
        assertEquals(0, isca.estado().toques(), "conferir não é tocar");
    }

    @Test
    void tocarConta_guardaAHoraDeBrasilia_eTemTeto() {
        Isca isca = new Isca(ISCA, "", RELOGIO);
        isca.tocar();
        isca.tocar();
        assertEquals(2, isca.estado().toques());
        assertEquals("12/11 20:30", isca.estado().ultimoToque(), "23:30 UTC = 20:30 em Brasília");
        for (int i = 0; i < Isca.MAXIMO_TOQUES + 50; i++) {
            isca.tocar();
        }
        assertEquals(Isca.MAXIMO_TOQUES, isca.estado().toques(), "o contador não cresce para sempre");
    }

    @Test
    void assinaturaFeitaComOTokenIsca_eReconhecida_eADeOutroTokenNao() {
        Isca isca = new Isca(ISCA, "", Clock.systemUTC());
        String hora = String.valueOf(System.currentTimeMillis() / 1000);
        byte[] corpo = new byte[0];
        String comAIsca = assinar(ISCA, hora, "a".repeat(32), corpo);
        String comOutro = assinar("outro-token-qualquer", hora, "b".repeat(32), corpo);
        assertTrue(isca.assinouComAIsca("GET", "/acoes/pendencias", hora, "a".repeat(32), corpo, comAIsca));
        assertFalse(isca.assinouComAIsca("GET", "/acoes/pendencias", hora, "b".repeat(32), corpo, comOutro));
        assertFalse(isca.assinouComAIsca("GET", "/acoes/pendencias", hora, "c".repeat(32), null, comAIsca),
                "sem o corpo guardado não dá para conferir");
    }

    private static String assinar(String token, String hora, String nonce, byte[] corpo) {
        byte[] sub = ProvaController.hmacBytes(token.getBytes(StandardCharsets.UTF_8), Assinatura.ROTULO_SUBCHAVE);
        return Assinatura.assinar(sub, "GET", "/acoes/pendencias", hora, nonce, corpo);
    }

    @Test
    void arquivoIsca_igual_mexido_eSumiu() throws Exception {
        Path arquivo = pasta.resolve("senhas-banco.txt");
        Files.writeString(arquivo, "banco inventado: senha-falsa-123\n");
        Isca isca = new Isca(ISCA, arquivo.toString(), RELOGIO);
        assertEquals(Isca.IGUAL, isca.estado().arquivo());

        Files.writeString(arquivo, "alguem mexeu aqui\n");
        assertEquals(Isca.MEXIDO, isca.estado().arquivo());

        Files.delete(arquivo);
        assertEquals(Isca.SUMIU, isca.estado().arquivo());
    }

    @Test
    void arquivoQueNaoDaParaVigiar_ficaSem_eNaoInventaAlarme() throws Exception {
        assertEquals(Isca.SEM, new Isca(ISCA, pasta.resolve("nao-existe.txt").toString(), RELOGIO).estado().arquivo());
        assertEquals(Isca.SEM, new Isca(ISCA, pasta.toString(), RELOGIO).estado().arquivo(), "pasta não é arquivo");
        Path grande = pasta.resolve("grande.txt");
        Files.write(grande, new byte[(int) Isca.MAXIMO_ARQUIVO + 1]);
        assertEquals(Isca.SEM, new Isca(ISCA, grande.toString(), RELOGIO).estado().arquivo());
    }

    @Test
    void estadoNuncaMostraOTokenNemOCaminho() throws Exception {
        Path arquivo = pasta.resolve("senhas-banco.txt");
        Files.writeString(arquivo, "x");
        Isca isca = new Isca(ISCA, arquivo.toString(), RELOGIO);
        isca.tocar();
        String texto = isca.estado().toString();
        assertFalse(texto.contains(ISCA) || texto.contains("senhas-banco") || texto.contains(pasta.toString()), texto);
    }
}
