package io.github.jjoseffelipe2028.bracos.acoes.video;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import io.github.jjoseffelipe2028.bracos.erros.EntradaInvalidaException;
import org.junit.jupiter.api.Test;

/** V2: cada alerta, os limites e a previsão. */
class AnalisadorDeRetencaoTest {

    private final AnalisadorDeRetencao analisador = new AnalisadorDeRetencao(new PortaoDoLaboratorio());

    private static RelatorioRequest pedido(String plataforma, int duracao, double proporcao, int media,
                                           List<Integer> quedas) {
        return new RelatorioRequest("Meu vídeo", plataforma, duracao, proporcao, media, 4.0, quedas);
    }

    private static boolean tem(Analise a, String trecho) {
        return a.achados().stream().anyMatch(x -> x.erro().contains(trecho));
    }

    @Test
    void videoPerfeito_forteSemAchados() {
        Analise a = analisador.analisar(pedido("facebook", 190, 60.0, 90, null));
        assertEquals("forte", a.previsao());
        assertTrue(a.dublagem().libera());
        assertTrue(a.achados().isEmpty());
    }

    @Test
    void facebook179s_alertaDe3Minutos() {
        Analise a = analisador.analisar(pedido("facebook", 179, 60.0, 90, null));
        assertTrue(tem(a, "abaixo de 3 min"));
        assertEquals("promissor, com ajustes", a.previsao());
    }

    @Test
    void facebook180s_semAlertaDe3Minutos() {
        assertFalse(tem(analisador.analisar(pedido("facebook", 180, 60.0, 90, null)), "abaixo de 3 min"));
    }

    @Test
    void youtube179s_regraDos3MinutosEhSoDoFacebook() {
        assertFalse(tem(analisador.analisar(pedido("youtube", 179, 60.0, 90, null)), "abaixo de 3 min"));
    }

    @Test
    void proporcao50_alertaDeSaidaAntesDoAnuncio() {
        Analise a = analisador.analisar(pedido("youtube", 190, 50.0, 80, null));
        assertTrue(tem(a, "antes de 1:00"));
    }

    @Test
    void media73sEmVideoLongo_alertaDeMedia() {
        Analise a = analisador.analisar(pedido("youtube", 190, 60.0, 73, null));
        assertTrue(tem(a, "abaixo de 1m14s"));
    }

    @Test
    void enterro_previsaoFracaENaoRepeteAlertaDeMedia() {
        Analise a = analisador.analisar(pedido("youtube", 190, 60.0, 20, null));
        assertEquals("fraco", a.previsao());
        assertTrue(tem(a, "enterro"));
        assertFalse(tem(a, "abaixo de 1m14s"), "o enterro já explica a média baixa");
    }

    @Test
    void naoLiberaSemEnterro_abaixoDaMeta() {
        assertEquals("abaixo da meta", analisador.analisar(pedido("youtube", 190, 40.0, 50, null)).previsao());
    }

    @Test
    void videoCurto_semPrevisaoESemAlertaDeEnterro() {
        Analise a = analisador.analisar(pedido("tiktok", 30, 0.0, 15, null));
        assertTrue(a.previsao().startsWith("sem previsão"));
        assertFalse(tem(a, "enterro"));
    }

    @Test
    void quedas_classificadasOrdenadasESemRepeticao() {
        Analise a = analisador.analisar(pedido("youtube", 190, 60.0, 90, List.of(120, 2, 55, 2)));
        List<String> erros = a.achados().stream().map(Achado::erro).toList();
        assertEquals(3, erros.size());
        assertTrue(erros.get(0).contains("gancho aos 0:02"));
        assertTrue(erros.get(1).contains("0:55, logo antes do anúncio"));
        assertEquals("queda aos 2:00", erros.get(2));
    }

    @Test
    void quedaAos3s_jaNaoEhGancho_eAos60s_jaNaoEhAntesDoAnuncio() {
        Analise a = analisador.analisar(pedido("youtube", 190, 60.0, 90, List.of(3, 60)));
        assertEquals("queda aos 0:03", a.achados().get(0).erro());
        assertEquals("queda aos 1:00", a.achados().get(1).erro());
    }

    @Test
    void quedaDepoisDoFim_entradaInvalida() {
        assertThrows(EntradaInvalidaException.class,
                () -> analisador.analisar(pedido("youtube", 100, 60.0, 50, List.of(101))));
    }

    @Test
    void mmss() {
        assertEquals("0:00", AnalisadorDeRetencao.mmss(0));
        assertEquals("1:14", AnalisadorDeRetencao.mmss(74));
        assertEquals("3:05", AnalisadorDeRetencao.mmss(185));
    }
}
