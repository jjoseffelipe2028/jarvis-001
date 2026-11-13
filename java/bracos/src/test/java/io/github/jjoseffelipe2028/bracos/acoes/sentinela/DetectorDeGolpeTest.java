package io.github.jjoseffelipe2028.bracos.acoes.sentinela;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

/** Ticket 023: golpes brasileiros de verdade (modelos comuns) e mensagens normais. 🎣 */
class DetectorDeGolpeTest {

    private final DetectorDeGolpe detector = new DetectorDeGolpe();

    private DetectorDeGolpe.Veredito v(String texto) {
        return detector.analisar(texto);
    }

    private static boolean temMotivo(DetectorDeGolpe.Veredito r, String trecho) {
        return r.motivos().stream().anyMatch(m -> m.contains(trecho));
    }

    @Test
    void falsoParente_comPix_eAlto() {
        DetectorDeGolpe.Veredito r = v("Oi mãe, troquei de número! Salva esse contato. "
                + "Preciso pagar um boleto urgente, me faz um PIX? Depois te devolvo.");
        assertEquals(DetectorDeGolpe.ALTO, r.nivel());
        assertTrue(temMotivo(r, "falso parente"));
        assertTrue(temMotivo(r, "PIX"));
        assertTrue(temMotivo(r, "Pressa"));
    }

    @Test
    void smsDeBancoFalso_comLinkImitando_eAlto() {
        DetectorDeGolpe.Veredito r = v("NUBANK: sua conta será bloqueada hoje. Regularize em http://nubank-seguranca.xyz/login");
        assertEquals(DetectorDeGolpe.ALTO, r.nivel());
        assertEquals(1, r.links());
        assertTrue(temMotivo(r, "imita banco"));
        assertTrue(temMotivo(r, ".xyz"));
        assertTrue(temMotivo(r, "sem cadeado"));
    }

    @Test
    void codigoDoWhatsApp_eAlto() {
        DetectorDeGolpe.Veredito r = v("Te mandei um código de verificação por engano, pode me passar o código que chegou? É urgente");
        assertEquals(DetectorDeGolpe.ALTO, r.nivel());
        assertTrue(temMotivo(r, "senha ou código"));
    }

    @Test
    void premioComLinkEncurtado_eAlto_masSoUmSinal_eSuspeito() {
        assertEquals(DetectorDeGolpe.ALTO, v("Parabéns! Você ganhou um prêmio, resgate em bit.ly/premio123").nivel());
        assertEquals(DetectorDeGolpe.SUSPEITO, v("Seu boleto vence dia 15, pague pelo app.").nivel());
    }

    @Test
    void truquesDeLink() {
        assertTrue(temMotivo(v("acesse https://www.itau.com.br@golpe.example/x"), "DEPOIS do @"));
        assertTrue(temMotivo(v("entra aqui http://192.168.10.5/premio"), "IP"));
        assertTrue(temMotivo(v("veja https://xn--nbank-wva.com/"), "punycode"));
        assertTrue(temMotivo(v("entra em https://nubаnk.com.br/login"), "punycode"), "\"а\" russo no lugar do \"a\"");
        assertTrue(temMotivo(v("clica bit.ly/3abcd"), "encurtado"));
        assertTrue(temMotivo(v("taxa da encomenda: correios-entrega.online/pagar"), "imita"));
    }

    @Test
    void siteOficial_naoEImitacao() {
        DetectorDeGolpe.Veredito r = v("Fatura disponível no app ou em https://www.nubank.com.br/ajuda e https://www.gov.br/receitafederal");
        assertFalse(temMotivo(r, "imita"), "nubank.com.br e gov.br são oficiais");
        assertTrue(DetectorDeGolpe.imitaMarca("nubank.com.br.golpe.example"), "marca oficial no começo não engana");
        assertFalse(DetectorDeGolpe.imitaMarca("app.itaucard.com.br"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Bom dia! Amanhã tem aula de Java às 19h, não esquece.",
            "O commit de hoje passou no CI, tudo verde.",
            "Vamos almoçar no sábado? Leva a sua mãe também."})
    void mensagemNormal_eBaixo(String texto) {
        DetectorDeGolpe.Veredito r = v(texto);
        assertEquals(DetectorDeGolpe.BAIXO, r.nivel(), texto);
        assertTrue(r.conselho().contains("nunca passe senha"));
    }

    // --- Ticket 029: Escudo do Criador (golpes contra quem tem canal) ---

    @Test
    void falsoPatrocinio_comAnexoExecutavel_eAlto() {
        DetectorDeGolpe.Veredito r = v("Olá! Somos da equipe de marketing e queremos patrocinar seu canal. "
                + "Baixe o media kit em https://mediafire.com/file/abc e abra o arquivo Contrato.scr. A senha do arquivo é 1234.");
        assertEquals(DetectorDeGolpe.ALTO, r.nivel());
        assertTrue(temMotivo(r, "roubam o canal"));
        assertTrue(temMotivo(r, "baixar ou instalar"));
        assertTrue(temMotivo(r, "armazenamento"));
    }

    @Test
    void falsoAvisoDeDireitosAutorais_eAlto() {
        DetectorDeGolpe.Veredito r = v("Aviso de direitos autorais: seu canal será removido em 24h. "
                + "Verifique sua conta em https://youtube-suporte.com/recurso");
        assertEquals(DetectorDeGolpe.ALTO, r.nivel());
        assertTrue(temMotivo(r, "YouTube Studio"));
        assertTrue(temMotivo(r, "fazer login"));
        assertTrue(temMotivo(r, "imita"));
    }

    @Test
    void plataformaOficial_naoEImitacao_masImitadorE() {
        assertFalse(DetectorDeGolpe.imitaMarca("studio.youtube.com"));
        assertFalse(DetectorDeGolpe.imitaMarca("youtu.be"));
        assertFalse(DetectorDeGolpe.imitaMarca("support.google.com"));
        assertFalse(DetectorDeGolpe.imitaMarca("clips.twitch.tv"));
        assertTrue(DetectorDeGolpe.imitaMarca("youtube.com.partner-verificacao.app"));
        assertTrue(DetectorDeGolpe.imitaMarca("instagram-selo.com"));
        assertTrue(DetectorDeGolpe.imitaMarca("tiktok-creators.gg"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Postei o vídeo novo no canal, depois me fala o que achou do corte.",
            "A live de GTA foi boa, o canal ganhou 30 inscritos hoje.",
            "Gravei a aula e mandei o link do meu vídeo: https://youtu.be/abc123",
            "Oi, tudo bem? Vi seu canal e gostei muito do conteúdo. Podemos conversar sobre uma parceria?"})
    void conversaNormalDeCriador_eBaixo(String texto) {
        assertEquals(DetectorDeGolpe.BAIXO, v(texto).nivel(), texto);
    }

    @Test
    void regrasDoCriador_naoTravamORegex() {
        for (String ataque : List.of(
                // Todos com até 4000 letras: é o limite que a API aceita (GolpeRequest e PropostaRequest)
                "baixe " + "a ".repeat(1990) + "jogo",
                "canal " + "x ".repeat(1990) + "sera removido",
                "verifique " + "sua ".repeat(990),
                ".zip".repeat(1000),
                "a.zip ".repeat(660))) {
            assertTimeoutPreemptively(Duration.ofSeconds(2), () -> v(ataque));
        }
    }

    // --- Ticket 035 (achado do duelo): ler os links em uma passada só ---

    @Test
    void textoNoLimite_respondeRapido_mesmoFeitoParaAtrasar() {
        v("aquecimento do regex: bit.ly/x e https://a.com"); // a 1ª chamada compila tudo
        for (String ataque : List.of("a.".repeat(2000), "a-".repeat(2000), ".zip".repeat(1000),
                "a.com.".repeat(660), "x/".repeat(2000), "www." + "x.".repeat(1998))) {
            assertTimeoutPreemptively(Duration.ofMillis(500), () -> v(ataque), "demorou: " + ataque.substring(0, 12));
        }
    }

    @Test
    void linkSolto_ePalavraComum() {
        assertTrue(DetectorDeGolpe.ehLinkSolto("bit.ly/premio"));
        assertTrue(DetectorDeGolpe.ehLinkSolto("correios-entrega.online/pagar"));
        assertTrue(DetectorDeGolpe.ehLinkSolto("site.com.br"));
        assertFalse(DetectorDeGolpe.ehLinkSolto("app"), "palavra sem ponto");
        assertFalse(DetectorDeGolpe.ehLinkSolto("fim.depois"), "final que não é de endereço");
        assertFalse(DetectorDeGolpe.ehLinkSolto("contrato.scr"));
        assertFalse(DetectorDeGolpe.ehLinkSolto(".com"));
        assertEquals("site.com.br", DetectorDeGolpe.semPontuacaoNoFim("site.com.br.)"));
    }

    @Test
    void pontoFinalDaFrase_naoViraImitacao_eOLinkContinuaContando() {
        DetectorDeGolpe.Veredito oficial = v("A fatura está em nubank.com.br.");
        assertEquals(1, oficial.links());
        assertFalse(temMotivo(oficial, "imita"), "nubank.com.br com ponto final continua sendo o site oficial");
        DetectorDeGolpe.Veredito falso = v("Regularize em nubank-seguranca.xyz. Hoje!");
        assertEquals(1, falso.links());
        assertTrue(temMotivo(falso, "imita"));
        assertEquals(0, v("Seu boleto vence dia 15, pague pelo app. Obrigado.").links());
    }

    @Test
    void respostaNuncaDevolveOTextoColado() {
        DetectorDeGolpe.Veredito r = v("<script>alert(1)</script> PIX urgente bit.ly/xss");
        for (String m : r.motivos()) {
            assertFalse(m.contains("<script>") || m.contains("xss"), "motivo é texto fixo: " + m);
        }
        assertFalse(r.conselho().contains("script"));
    }

    @Test
    void cadaMotivoContaUmaVez_eLinksTemLimite() {
        StringBuilder muitos = new StringBuilder("PIX PIX PIX ");
        for (int i = 0; i < 50; i++) {
            muitos.append("bit.ly/a").append(i).append(' ');
        }
        DetectorDeGolpe.Veredito r = v(muitos.toString());
        assertEquals(DetectorDeGolpe.MAXIMO_LINKS, r.links());
        assertEquals(r.motivos().size(), List.copyOf(new java.util.LinkedHashSet<>(r.motivos())).size(), "sem motivo repetido");
        assertEquals(4, r.pontos(), "PIX (2) + encurtador (2), cada um uma vez só");
    }

    @Test
    void textoGrandeEEstranho_naoTravaORegex() {
        // ReDoS: textos feitos para o regex "voltar atrás" sem parar. Tem que responder rápido.
        for (String ataque : List.of(
                "a".repeat(4000) + "." + "-".repeat(4000) + "@".repeat(2000),
                "a-".repeat(2000),
                "a.".repeat(2000),
                "http://" + "a@".repeat(2000),
                "www." + "x.".repeat(2000) + "com")) {
            assertTimeoutPreemptively(Duration.ofSeconds(2), () -> v(ataque));
        }
    }
}
