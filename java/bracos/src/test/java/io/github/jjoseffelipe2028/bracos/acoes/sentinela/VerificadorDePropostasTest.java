package io.github.jjoseffelipe2028.bracos.acoes.sentinela;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;

/** Ticket 030 (V4): propostas de marca de verdade × golpes para roubar o canal. Marcas INVENTADAS. 🤝🔎 */
class VerificadorDePropostasTest {

    private final VerificadorDePropostas verificador = new VerificadorDePropostas(new DetectorDeGolpe());

    private static final String PROPOSTA_NORMAL =
            "Olá! Somos da TecnoBrasa e gostamos do seu canal. Podemos marcar uma conversa sobre uma parceria? "
                    + "Mando o contrato em PDF depois da reunião.";

    private static boolean temMotivo(VerificadorDePropostas.Parecer p, String trecho) {
        return p.motivos().stream().anyMatch(m -> m.contains(trecho));
    }

    @Test
    void propostaNormal_doDominioDaMarca_eBaixo_masOsPassosVemSempre() {
        VerificadorDePropostas.Parecer p = verificador.analisar(PROPOSTA_NORMAL, "parcerias@tecnobrasa.com.br", "TecnoBrasa");
        assertEquals(DetectorDeGolpe.BAIXO, p.nivel(), p.motivos().toString());
        assertEquals(VerificadorDePropostas.PASSOS, p.passos());
        assertTrue(p.conselho().contains("siga os passos"));
    }

    @Test
    void marcaComEmailGratuito_eSuspeito() {
        VerificadorDePropostas.Parecer p = verificador.analisar(PROPOSTA_NORMAL, "tecnobrasa.marketing@gmail.com", "TecnoBrasa");
        assertEquals(DetectorDeGolpe.SUSPEITO, p.nivel());
        assertTrue(temMotivo(p, "e-mail gratuito"));
    }

    @Test
    void dominioComNomeDaMarcaEAlgoAMais_eImitacao() {
        VerificadorDePropostas.Parecer p = verificador.analisar(PROPOSTA_NORMAL, "Time TecnoBrasa <contato@tecnobrasa-parcerias.com>", "Tecno Brasa");
        assertEquals(DetectorDeGolpe.SUSPEITO, p.nivel());
        assertTrue(temMotivo(p, "algo a mais"));
    }

    @Test
    void dominioQueNaoTemNadaAVerComAMarca() {
        VerificadorDePropostas.Parecer p = verificador.analisar(PROPOSTA_NORMAL, "contato@agencia-xyz.net", "TecnoBrasa");
        assertTrue(temMotivo(p, "não bate com o nome da marca"));
    }

    @Test
    void golpeCompleto_eAlto() {
        VerificadorDePropostas.Parecer p = verificador.analisar(
                "Parceria paga de R$ 5.000! Baixe o media kit em https://mediafire.com/file/x e abra o Contrato.scr. "
                        + "A senha do arquivo é 2026. Responda hoje, última chance.",
                "tecnobrasa.collab@outlook.com", "TecnoBrasa");
        assertEquals(DetectorDeGolpe.ALTO, p.nivel());
        assertEquals(1, p.links());
        assertTrue(temMotivo(p, "roubam o canal"));
        assertTrue(temMotivo(p, "e-mail gratuito"));
        assertTrue(p.conselho().contains("Não responda"));
    }

    @Test
    void remetenteComTruqueDeEndereco_eAcusado() {
        assertTrue(temMotivo(verificador.analisar(PROPOSTA_NORMAL, "a@tecnobrasa.xyz", "TecnoBrasa"), ".xyz"));
        assertTrue(temMotivo(verificador.analisar(PROPOSTA_NORMAL, "a@xn--tecnobrsa-x1a.com", "TecnoBrasa"), "punycode"));
        assertTrue(temMotivo(verificador.analisar(PROPOSTA_NORMAL, "suporte@youtube-parceiros.com", null), "imita"));
    }

    @Test
    void semRemetenteOuSemMarca_soAnalisaOTexto() {
        assertEquals(DetectorDeGolpe.BAIXO, verificador.analisar(PROPOSTA_NORMAL, null, null).nivel());
        assertEquals(DetectorDeGolpe.BAIXO, verificador.analisar(PROPOSTA_NORMAL, "  ", "TecnoBrasa").nivel());
        // E-mail gratuito SEM dizer que é marca (ex.: outro criador): não é sinal sozinho
        assertEquals(DetectorDeGolpe.BAIXO, verificador.analisar(PROPOSTA_NORMAL, "fulano@gmail.com", null).nivel());
    }

    @Test
    void remetenteEmFormatoEstranho_contaPonto() {
        for (String ruim : List.of("sem-arroba", "@so-dominio.com", "nome@", "a@b", "a@dominio com espaco.com", "a@..com")) {
            assertTrue(temMotivo(verificador.analisar(PROPOSTA_NORMAL, ruim, null), "formato estranho"), ruim);
        }
    }

    @Test
    void dominioENomePrincipal() {
        assertEquals("marca.com.br", VerificadorDePropostas.dominioDe("Fulano <Contato@Marca.com.br>"));
        assertEquals("marca.com", VerificadorDePropostas.dominioDe(" a@b@marca.com "));
        assertNull(VerificadorDePropostas.dominioDe(null));
        assertEquals("marca", VerificadorDePropostas.nomePrincipal("mail.marca.com.br"));
        assertEquals("marca", VerificadorDePropostas.nomePrincipal("marca.co.uk"));
        assertEquals("marca-parcerias", VerificadorDePropostas.nomePrincipal("marca-parcerias.com"));
        assertEquals("marca", VerificadorDePropostas.nomePrincipal("news.marca.io"));
    }

    @Test
    void respostaSoTemTextoFixo_nadaDoPedidoVolta() {
        VerificadorDePropostas.Parecer p = verificador.analisar("<script>alert(1)</script> PIX urgente",
                "<img src=x>@evil-zzz.com", "<b>MarcaZzz</b>");
        String tudo = p.motivos() + p.conselho() + p.passos();
        assertFalse(tudo.contains("<script>") || tudo.contains("zzz") || tudo.contains("<img") || tudo.contains("<b>"), tudo);
    }

    @Test
    void entradaGrandeEEstranha_naoTrava() {
        for (String ataque : List.of("a@" + "a.".repeat(120) + "com", "<".repeat(254), "a@" + "-".repeat(250), "@".repeat(254))) {
            assertTimeoutPreemptively(Duration.ofSeconds(2), () -> verificador.analisar("oi", ataque, "m".repeat(60)));
        }
    }
}
