package io.github.jjoseffelipe2028.bracos.central;

import java.util.Map;

import io.github.jjoseffelipe2028.bracos.acoes.pedidos.PedidoRequest;
import io.github.jjoseffelipe2028.bracos.acoes.pedidos.PedidoService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * POST /central/acoes/pedido-001 (Ticket 060): o pedido revisado vira nota na caixa da 001. 📬🔒
 * - Só chega aqui depois do "Confirmar" na tela. O chat sozinho nunca grava nada.
 * - Porteiros da Central rodam antes: Host, cabeçalho, Origin obrigatório na escrita e crachá.
 * - Log sem o texto: só quem, o quê e o resultado.
 */
@RestController
@RequestMapping("/central/acoes")
public class CentralPedidoController {

    private static final Logger log = LoggerFactory.getLogger(CentralPedidoController.class);

    private final PedidoService pedidos;

    public CentralPedidoController(PedidoService pedidos) {
        this.pedidos = pedidos;
    }

    @PostMapping("/pedido-001")
    public Map<String, String> anotar(@Valid @RequestBody PedidoRequest pedido) {
        try {
            String arquivo = pedidos.anotar(pedido.texto());
            log.info("cliente=central acao=pedido001 resultado=ok");
            return Map.of("arquivo", arquivo, "resultado", "ok");
        } catch (RuntimeException e) {
            log.warn("cliente=central acao=pedido001 resultado=erro motivo={}", e.getClass().getSimpleName());
            throw e;
        }
    }
}
