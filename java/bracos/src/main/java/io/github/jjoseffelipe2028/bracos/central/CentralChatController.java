package io.github.jjoseffelipe2028.bracos.central;

import java.time.Clock;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import io.github.jjoseffelipe2028.bracos.acoes.conversa.ChatRequest;
import io.github.jjoseffelipe2028.bracos.acoes.conversa.ConversaService;
import io.github.jjoseffelipe2028.bracos.acoes.conversa.RoteadorDoChat;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * POST /central/acoes/chat: falar com o Jarvis direto na tela da Central. 💬
 * - Ticket 039: conversa com a IA local. Ticket 046: o chat também PEDE ações da lista fechada.
 * - Os porteiros da Central rodam antes (Host, cabeçalho, Origin obrigatório no POST e crachá).
 * - Primeiro o {@link RoteadorDoChat} (regras fixas). Se for ação, a resposta é só {@code {"acao": nome}}:
 *   quem executa é a tela, pela rota que já existe. A IA nunca escolhe ação.
 * - Pedido de mudar o PC é recusado com texto fixo e nem chega na IA.
 * - O log não guarda a conversa: só o nome da ação ou o tamanho da resposta.
 */
@RestController
@RequestMapping("/central/acoes")
public class CentralChatController {

    private static final Logger log = LoggerFactory.getLogger(CentralChatController.class);
    private static final ZoneId BRASILIA = ZoneId.of("America/Sao_Paulo");
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm 'de' EEEE, dd/MM", Locale.forLanguageTag("pt-BR"));

    static final String RECUSA = "Isso eu não faço pelo chat: aqui eu só leio e aviso. "
            + "O que muda o PC continua com você (ou com a 001, com o seu OK).";
    static final String RONDA = "🛡️ A ronda do defensor roda no terminal: python defensor.py "
            + "(o botão na Central vem no próximo lote).";
    static final String GOLPE_VAZIO = "Cole a mensagem depois dos dois pontos. Exemplo: isso é golpe: <mensagem>";

    private final ConversaService conversa;
    private final RoteadorDoChat roteador;
    private final Clock relogio;

    @Autowired
    public CentralChatController(ConversaService conversa, RoteadorDoChat roteador) {
        this(conversa, roteador, Clock.systemUTC());
    }

    CentralChatController(ConversaService conversa, RoteadorDoChat roteador, Clock relogio) {
        this.conversa = conversa;
        this.roteador = roteador;
        this.relogio = relogio;
    }

    @PostMapping("/chat")
    public Map<String, String> chat(@Valid @RequestBody ChatRequest pedido) {
        Optional<RoteadorDoChat.Rota> rota = roteador.rotear(pedido.texto());
        if (rota.isPresent()) {
            RoteadorDoChat.Acao acao = rota.get().acao();
            log.info("cliente=central acao=chat rota={}", acao.name().toLowerCase(Locale.ROOT));
            return responderAcao(acao, rota.get().argumento());
        }
        try {
            String resposta = conversa.responder(pedido.texto(), pedido.historico());
            log.info("cliente=central acao=chat resultado=ok letras={}", resposta.length());
            return Map.of("resposta", resposta);
        } catch (RuntimeException e) {
            log.warn("cliente=central acao=chat resultado=erro motivo={}", e.getClass().getSimpleName());
            throw e;
        }
    }

    private Map<String, String> responderAcao(RoteadorDoChat.Acao acao, String argumento) {
        return switch (acao) {
            case PROIBIDA -> Map.of("resposta", RECUSA);
            case RONDA -> Map.of("resposta", RONDA);
            case HORA -> Map.of("resposta", "🕒 Agora são "
                    + ZonedDateTime.now(relogio).withZoneSameInstant(BRASILIA).format(HORA) + ".");
            case GOLPE -> argumento == null || argumento.isBlank()
                    ? Map.of("resposta", GOLPE_VAZIO)
                    : Map.of("acao", "golpe", "texto", argumento); // a própria mensagem que o José colou
            case PAGINA -> Map.of("acao", "pagina", "pagina", argumento); // um dos 4 nomes fixos do roteador
            default -> Map.of("acao", acao.name().toLowerCase(Locale.ROOT));
        };
    }
}
