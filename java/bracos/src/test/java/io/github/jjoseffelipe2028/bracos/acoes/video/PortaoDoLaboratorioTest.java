package io.github.jjoseffelipe2028.bracos.acoes.video;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.jjoseffelipe2028.bracos.erros.EntradaInvalidaException;
import org.junit.jupiter.api.Test;

/** Regra do Laboratório Brasil: testa os LIMITES exatos de cada benchmark. */
class PortaoDoLaboratorioTest {

    private final PortaoDoLaboratorio portao = new PortaoDoLaboratorio();

    @Test
    void proporcaoExatamente50_naoLibera() {
        assertFalse(portao.avaliar(190, 50.0, 60).libera());
    }

    @Test
    void proporcaoAcimaDe50_libera() {
        Avaliacao a = portao.avaliar(190, 50.1, 60);
        assertTrue(a.libera());
        assertEquals(1, a.motivos().size());
        assertTrue(a.motivos().get(0).contains("50%"));
    }

    @Test
    void media73s_naoLibera() {
        assertFalse(portao.avaliar(190, 30.0, 73).libera());
    }

    @Test
    void media74s_libera() {
        Avaliacao a = portao.avaliar(190, 30.0, 74);
        assertTrue(a.libera());
        assertTrue(a.motivos().get(0).contains("1m14s"));
    }

    @Test
    void batendoOsDois_mostraOsDoisMotivos() {
        Avaliacao a = portao.avaliar(190, 60.0, 90);
        assertTrue(a.libera());
        assertEquals(2, a.motivos().size());
    }

    @Test
    void naoBatendoNenhum_bloqueiaComOsDoisMotivos() {
        Avaliacao a = portao.avaliar(190, 40.0, 50);
        assertFalse(a.libera());
        assertEquals(2, a.motivos().size());
    }

    @Test
    void media20s_alertaDeEnterroMesmoComProporcaoBoa() {
        // Proporção ótima não salva vídeo com média de 20 s: algo está errado no tráfego
        Avaliacao a = portao.avaliar(190, 90.0, 20);
        assertFalse(a.libera());
        assertTrue(a.motivos().get(0).contains("enterro"));
    }

    @Test
    void media21s_naoEAlertaDeEnterro() {
        Avaliacao a = portao.avaliar(190, 90.0, 21);
        assertTrue(a.libera(), "21 s já sai da faixa de enterro e a proporção libera");
    }

    @Test
    void videoDe59s_curtoDemais() {
        Avaliacao a = portao.avaliar(59, 90.0, 50);
        assertFalse(a.libera());
        assertTrue(a.motivos().get(0).contains("menos de 1 min"));
    }

    @Test
    void videoDe60s_jaEntraNaRegra() {
        assertTrue(portao.avaliar(60, 90.0, 50).libera());
    }

    @Test
    void mediaMaiorQueADuracao_entradaInvalida() {
        assertThrows(EntradaInvalidaException.class, () -> portao.avaliar(100, 50.0, 101));
    }

    @Test
    void motivosNaoPodemSerAlteradosDeFora() {
        Avaliacao a = portao.avaliar(190, 60.0, 90);
        assertThrows(UnsupportedOperationException.class, () -> a.motivos().add("hack"));
    }
}
