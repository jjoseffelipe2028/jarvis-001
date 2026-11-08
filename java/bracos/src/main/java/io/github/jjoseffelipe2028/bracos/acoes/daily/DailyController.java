package io.github.jjoseffelipe2028.bracos.acoes.daily;

import java.util.Map;

import io.github.jjoseffelipe2028.bracos.auditoria.Cliente;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * POST /acoes/daily → escreve uma linha no daily de hoje. 🔒
 * O token já foi conferido pelo TokenInterceptor antes de chegar aqui.
 * Exemplo de resposta: {"arquivo":"2026-11-08.md","resultado":"ok"}
 */
@RestController
@RequestMapping("/acoes")
public class DailyController {

    private static final Logger log = LoggerFactory.getLogger(DailyController.class);

    private final DailyService service;

    public DailyController(DailyService service) {
        this.service = service;
    }

    @PostMapping("/daily")
    public Map<String, String> daily(
            @Valid @RequestBody DailyRequest pedido,
            @RequestHeader(value = Cliente.CABECALHO, required = false) String clienteBruto) {

        String cliente = Cliente.de(clienteBruto); // só python/central/desconhecido
        try {
            String arquivo = service.registrar(pedido.texto());
            // Nunca loga o texto nem o caminho: só quem pediu, a ação e o resultado
            log.info("cliente={} acao=daily resultado=ok", cliente);
            return Map.of("arquivo", arquivo, "resultado", "ok");
        } catch (RuntimeException e) {
            // Motivo = nome da classe do erro (texto fixo, não vem de fora)
            log.warn("cliente={} acao=daily resultado=erro motivo={}", cliente, e.getClass().getSimpleName());
            throw e;
        }
    }
}
