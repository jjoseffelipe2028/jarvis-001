package io.github.jjoseffelipe2028.bracos.acoes.conversa;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import io.github.jjoseffelipe2028.bracos.acoes.conversa.RoteadorDoChat.Acao;
import org.junit.jupiter.api.Test;

/** Ticket 046: o roteador do chat escolhe UMA ação da lista com regras fixas, ou deixa a frase ir para a conversa. 🎯 */
class RoteadorDoChatTest {

    private final RoteadorDoChat roteador = new RoteadorDoChat();

    private Acao acao(String frase) {
        return roteador.rotear(frase).map(RoteadorDoChat.Rota::acao).orElse(null);
    }

    @Test
    void pedidosDaLista_viramAAcaoCerta() {
        Map<String, Acao> casos = Map.ofEntries(
                Map.entry("quais minhas pendências?", Acao.PENDENCIAS),
                Map.entry("Jarvis, o que falta?", Acao.PENDENCIAS),
                Map.entry("pendências", Acao.PENDENCIAS),
                Map.entry("tenho algo sem commit?", Acao.GIT_STATUS),
                Map.entry("status do git", Acao.GIT_STATUS),
                Map.entry("vigia as portas", Acao.PORTAS),
                Map.entry("tem porta aberta?", Acao.PORTAS),
                Map.entry("sentinela", Acao.PORTAS),
                Map.entry("alguém mexeu nas travas?", Acao.INTEGRIDADE),
                Map.entry("confere as travas", Acao.INTEGRIDADE),
                Map.entry("confere a isca", Acao.ISCA),
                Map.entry("alguém tocou na isca?", Acao.ISCA),
                Map.entry("que horas são?", Acao.HORA),
                Map.entry("faz a ronda", Acao.RONDA),
                Map.entry("super defensor", Acao.RONDA));
        casos.forEach((frase, esperado) -> assertEquals(esperado, acao(frase), frase));
    }

    @Test
    void conversa_naoViraAcao() {
        for (String frase : List.of("o que é um commit?", "me explica o que é uma porta aberta",
                "qual a capital do Brasil?", "golpe do pix é comum", "me conta sobre a isca de pesca",
                "como funciona a ronda de um vigia?", "oi", "   ")) {
            assertTrue(roteador.rotear(frase).isEmpty(), frase);
        }
        assertTrue(roteador.rotear(null).isEmpty());
    }

    @Test
    void pedidoDeMudarOPc_eRecusado() {
        for (String frase : List.of("apaga a pasta do jarvis", "Jarvis, instala o WhiteRabbitNeo", "faz um pix de 50",
                "manda meu .env", "roda esse comando: rm -rf", "posta o corte", "escreve no arquivo", "abre o chrome")) {
            assertEquals(Acao.PROIBIDA, acao(frase), frase);
        }
    }

    @Test
    void golpe_levaAMensagemOriginal_comAcento_eAntesDaRecusa() {
        RoteadorDoChat.Rota rota = roteador.rotear("Isso é golpe: Mãe, faz um PIX urgente pra mim").orElseThrow();
        assertEquals(Acao.GOLPE, rota.acao());
        assertEquals("Mãe, faz um PIX urgente pra mim", rota.argumento(), "a mensagem colada não vira pedido proibido");
        assertEquals("", roteador.rotear("isso é golpe:").orElseThrow().argumento());
    }

    @Test
    void pagina_soDevolveUmDosNomesFixos() {
        assertEquals("cyber", roteador.rotear("abre a cyber").orElseThrow().argumento());
        assertEquals("videos", roteador.rotear("vai pra página de vídeo").orElseThrow().argumento());
        assertEquals("jogos", roteador.rotear("abre jogos!").orElseThrow().argumento());
        assertEquals("inicio", roteador.rotear("Jarvis, abre o início").orElseThrow().argumento());
        assertEquals(Acao.PROIBIDA, acao("abre a cyber e apaga tudo"), "frase com resto não é só abrir página");
    }

    @Test
    void semAcento_mantemOTamanho() {
        for (String texto : List.of("Ação São João", "İstanbul", "ﬁm", "😀 emoji")) {
            assertEquals(texto.length(), RoteadorDoChat.semAcento(texto).length(), texto);
        }
    }

    // ---------- Ticket 060: caixa de pedidos para a 001 ----------

    @Test
    void pedidoPara001_viraPedido_comOTextoOriginal() {
        RoteadorDoChat.Rota r = roteador.rotear("pede pra 001: cria um mini jogo de cobrinha com placar").orElseThrow();
        assertEquals(Acao.PEDIDO_001, r.acao());
        assertEquals("cria um mini jogo de cobrinha com placar", r.argumento());
        assertEquals("revisa a ação daily", roteador.rotear("Jarvis, peça para a 001: revisa a ação daily").orElseThrow().argumento());
        assertEquals(Acao.PEDIDO_001, acao("anota pra 001 - estudar Spring Security"));
        assertEquals(Acao.PEDIDO_001, acao("pede pra zero zero um: ver o README"));
    }

    @Test
    void pedidoPara001_comPalavraProibida_eRecado_naoOrdem() {
        // "instala" e "apaga" aqui são parte do recado para a 001, não ordem para o Jarvis
        RoteadorDoChat.Rota r = roteador.rotear("pede pra 001: instala o gitleaks e apaga o log velho").orElseThrow();
        assertEquals(Acao.PEDIDO_001, r.acao());
        assertEquals("instala o gitleaks e apaga o log velho", r.argumento());
    }

    @Test
    void falarDa001SemDoisPontos_naoViraPedido() {
        assertTrue(roteador.rotear("o que a 001 faz?").isEmpty());
        assertTrue(roteador.rotear("pede pra 001 cria um jogo").isEmpty()); // sem ":" não é pedido
        assertEquals(Acao.PROIBIDA, acao("apaga o pedido pra 001: x")); // começa com "apaga": recusa
    }

    // ---------- Ticket 062: "bom dia" abre o jornal ----------

    @Test
    void bomDia_viraJornal() {
        for (String frase : List.of("bom dia", "Bom dia, Jarvis!", "boa tarde", "Jarvis, boa noite", "jornal", "o jornal do dia")) {
            assertEquals(Acao.JORNAL, acao(frase), frase);
        }
    }

    @Test
    void bomDiaComPergunta_continuaConversa() {
        for (String frase : List.of("bom dia, me explica o que é um record", "bom dia jarvis tudo bem?", "li no jornal sobre java")) {
            assertTrue(roteador.rotear(frase).isEmpty(), frase);
        }
    }

    // ---------- Ticket 072: pergunta para a dupla ----------

    @Test
    void pedidoParaDupla_viraPedidoDupla_comOTextoOriginal() {
        RoteadorDoChat.Rota r = roteador.rotear("pede pra dupla: vale usar records aqui?").orElseThrow();
        assertEquals(Acao.PEDIDO_DUPLA, r.acao());
        assertEquals("vale usar records aqui?", r.argumento());
        assertEquals(Acao.PEDIDO_DUPLA, acao("Jarvis, pergunta pra dupla: qual banco usar?"));
    }

    @Test
    void pedidoParaDupla_comPalavraProibida_eRecado() {
        RoteadorDoChat.Rota r = roteador.rotear("pede pra dupla: instala o X e apaga o Y?").orElseThrow();
        assertEquals(Acao.PEDIDO_DUPLA, r.acao());
        assertEquals("instala o X e apaga o Y?", r.argumento());
    }

    @Test
    void falarDaDuplaSemDoisPontos_naoViraPedido_eA001ContinuaSeparada() {
        assertTrue(roteador.rotear("o que a dupla faz?").isEmpty());
        assertEquals(Acao.PEDIDO_001, acao("pede pra 001: x"));
        assertEquals(Acao.PROIBIDA, acao("apaga o pede pra dupla: x"));
    }
}
