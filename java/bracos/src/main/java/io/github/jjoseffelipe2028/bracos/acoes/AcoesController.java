package io.github.jjoseffelipe2028.bracos.acoes;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Braços do Jarvis: as AÇÕES que o Python pode pedir.
 * Só existe o que está escrito aqui (allowlist). 🔒
 */
@RestController            // esta classe responde pedidos da web e devolve JSON
@RequestMapping("/acoes")  // todos os endereços daqui começam com /acoes
public class AcoesController {

    // Fuso horário fixo: o horário de Brasília
    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");

    // Formatadores criados uma vez só (são imutáveis, seguros para chamadas ao mesmo tempo)
    private static final DateTimeFormatter FORMATO_HORA = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter FORMATO_DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    // Log de auditoria: registra qual ação foi pedida e o resultado
    private static final Logger log = LoggerFactory.getLogger(AcoesController.class);

    /**
     * GET /acoes/hora → devolve a data e a hora de agora.
     * Exemplo: {"hora":"23:45","data":"01/11/2026"}
     */
    @GetMapping("/hora")
    public Map<String, String> hora() {
        ZonedDateTime agora = ZonedDateTime.now(FUSO);

        String hora = agora.format(FORMATO_HORA);
        String data = agora.format(FORMATO_DATA);

        log.info("acao=hora resultado=ok");
        return Map.of("hora", hora, "data", data);
    }
}
