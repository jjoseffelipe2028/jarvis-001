package io.github.jjoseffelipe2028.bracos.acoes;

// ⚠️ Escrito pela 001 em 01/11/2026 para revisão.
// TAREFA DO JOSÉ: apagar e redigitar sozinho (regra "Java eu digito").

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

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

    /**
     * GET /acoes/hora → devolve a data e a hora de agora.
     * Exemplo: {"hora":"23:45","data":"01/11/2026"}
     */
    @GetMapping("/hora")
    public Map<String, String> hora() {
        ZonedDateTime agora = ZonedDateTime.now(FUSO);

        String hora = agora.format(DateTimeFormatter.ofPattern("HH:mm"));
        String data = agora.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));

        return Map.of("hora", hora, "data", data);
    }
}
