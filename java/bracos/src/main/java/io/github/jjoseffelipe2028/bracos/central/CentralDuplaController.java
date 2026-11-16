package io.github.jjoseffelipe2028.bracos.central;

import java.util.List;
import java.util.Map;

import io.github.jjoseffelipe2028.bracos.acoes.dupla.DuplaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * GET /central/acoes/dupla (Ticket 072, parte C): "Última palavra da dupla". 🤝🔒
 * - Só leitura, sem entrada. A API nunca chama IA da nuvem: só lê o que o /dupla já gravou em docs/reviews.
 * - Volta só data, título curto e veredito (enum). Nunca o texto da nota.
 */
@RestController
@RequestMapping("/central/acoes")
public class CentralDuplaController {

    private static final Logger log = LoggerFactory.getLogger(CentralDuplaController.class);

    private final DuplaService dupla;

    public CentralDuplaController(DuplaService dupla) {
        this.dupla = dupla;
    }

    @GetMapping("/dupla")
    public Map<String, List<DuplaService.Registro>> dupla() {
        try {
            List<DuplaService.Registro> registros = dupla.ultimos();
            log.info("cliente=central acao=dupla resultado=ok registros={}", registros.size());
            return Map.of("registros", registros);
        } catch (RuntimeException e) {
            log.warn("cliente=central acao=dupla resultado=erro motivo={}", e.getClass().getSimpleName());
            throw e;
        }
    }
}
