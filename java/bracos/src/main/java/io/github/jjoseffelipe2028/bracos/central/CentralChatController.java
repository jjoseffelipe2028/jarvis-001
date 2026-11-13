package io.github.jjoseffelipe2028.bracos.central;

import java.util.Map;

import io.github.jjoseffelipe2028.bracos.acoes.conversa.ChatRequest;
import io.github.jjoseffelipe2028.bracos.acoes.conversa.ConversaService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * POST /central/acoes/chat (Ticket 039): falar com a IA local direto na tela da Central. 💬
 * - Os porteiros da Central rodam antes (Host, cabeçalho, Origin obrigatório no POST e crachá).
 * - A IA só conversa: nada é executado e nada é escrito no PC.
 * - O log não guarda a conversa: só quem pediu e o tamanho da resposta.
 */
@RestController
@RequestMapping("/central/acoes")
public class CentralChatController {

    private static final Logger log = LoggerFactory.getLogger(CentralChatController.class);

    private final ConversaService conversa;

    public CentralChatController(ConversaService conversa) {
        this.conversa = conversa;
    }

    @PostMapping("/chat")
    public Map<String, String> chat(@Valid @RequestBody ChatRequest pedido) {
        try {
            String resposta = conversa.responder(pedido.texto(), pedido.historico());
            log.info("cliente=central acao=chat resultado=ok letras={}", resposta.length());
            return Map.of("resposta", resposta);
        } catch (RuntimeException e) {
            log.warn("cliente=central acao=chat resultado=erro motivo={}", e.getClass().getSimpleName());
            throw e;
        }
    }
}
