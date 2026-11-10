package io.github.jjoseffelipe2028.bracos.central;

import java.util.Map;

import io.github.jjoseffelipe2028.bracos.acoes.daily.DailyRequest;
import io.github.jjoseffelipe2028.bracos.acoes.daily.DailyService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * POST /central/acoes/daily (Ticket 020): a 1ª ação da Central que ESCREVE no cofre. 🔒
 * - Os porteiros rodam antes: Host, cabeçalho da Central, crachá e Origin OBRIGATÓRIO
 *   (na escrita, pedido sem Origin é recusado; ver CentralInterceptor).
 * - Reaproveita o MESMO DailyService da voz: mesma pasta do .env, mesmo "desarme" do texto,
 *   mesmo limite de 2000 caracteres (DailyRequest). Nada de arquivo ou pasta no pedido.
 * - Log sem o texto: só quem, o quê e o resultado.
 * - O EventosInterceptor anota no quadro: o escritório reage e o Jarvis fala (Tickets 017/018).
 */
@RestController
@RequestMapping("/central/acoes")
public class CentralDailyController {

    private static final Logger log = LoggerFactory.getLogger(CentralDailyController.class);

    private final DailyService daily;

    public CentralDailyController(DailyService daily) {
        this.daily = daily;
    }

    @PostMapping("/daily")
    public Map<String, String> daily(@Valid @RequestBody DailyRequest pedido) {
        try {
            String arquivo = daily.registrar(pedido.texto());
            log.info("cliente=central acao=daily resultado=ok");
            return Map.of("arquivo", arquivo, "resultado", "ok");
        } catch (RuntimeException e) {
            // Motivo = nome da classe do erro (texto fixo, não vem de fora)
            log.warn("cliente=central acao=daily resultado=erro motivo={}", e.getClass().getSimpleName());
            throw e;
        }
    }
}
