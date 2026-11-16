package io.github.jjoseffelipe2028.bracos.central;

import io.github.jjoseffelipe2028.bracos.acoes.ronda.RondaService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * POST /central/acoes/ronda (Ticket 061): botão 🛡️ Ronda do defensor. 🔒
 * - Sem corpo: não há nada para o José digitar, então não existe entrada para validar nem para injetar.
 * - POST porque roda um programa (e o defensor.py grava a nota de incidente): pede Origin e crachá.
 * - Log só com o nível geral, nunca as frases dos vigias.
 */
@RestController
@RequestMapping("/central/acoes")
public class CentralRondaController {

    private static final Logger log = LoggerFactory.getLogger(CentralRondaController.class);

    private final RondaService ronda;

    public CentralRondaController(RondaService ronda) {
        this.ronda = ronda;
    }

    /** GET /central/acoes/ronda/ultima (Ticket 062): só lê a última ronda guardada. Nenhuma → 204. */
    @GetMapping("/ronda/ultima")
    public ResponseEntity<RondaService.Ultima> ultima() {
        log.info("cliente=central acao=ronda-ultima resultado=ok");
        return ronda.ultima().map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping("/ronda")
    public RondaService.Resultado ronda() {
        try {
            RondaService.Resultado r = ronda.fazerRonda();
            log.info("cliente=central acao=ronda resultado=ok geral={}", r.geral());
            return r;
        } catch (RuntimeException e) {
            log.warn("cliente=central acao=ronda resultado=erro motivo={}", e.getClass().getSimpleName());
            throw e;
        }
    }
}
