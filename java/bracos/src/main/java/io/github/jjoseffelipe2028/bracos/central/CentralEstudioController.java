package io.github.jjoseffelipe2028.bracos.central;

import io.github.jjoseffelipe2028.bracos.acoes.estudio.EstudioRequest;
import io.github.jjoseffelipe2028.bracos.acoes.estudio.EstudioService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Estúdio 001 pela Central (Ticket 047). 🎮
 * - POST /central/acoes/estudio: começa um pedido (001 planeja → 002 revisa → 001 constrói).
 * - GET  /central/acoes/estudio: em que etapa está.
 * - Os porteiros da Central rodam antes (Host, cabeçalho, Origin obrigatório no POST e crachá).
 *   A tela só manda depois do "Revisar → Confirmar" do José.
 * - O log não guarda o pedido: só que começou e se teve revisão.
 */
@RestController
@RequestMapping("/central/acoes")
public class CentralEstudioController {

    private static final Logger log = LoggerFactory.getLogger(CentralEstudioController.class);

    private final EstudioService estudio;

    public CentralEstudioController(EstudioService estudio) {
        this.estudio = estudio;
    }

    @PostMapping("/estudio")
    public EstudioService.Estado iniciar(@Valid @RequestBody EstudioRequest pedido) {
        boolean revisao = pedido.comRevisao();
        int letras = pedido.texto().length(); // só o tamanho: o pedido não vai para o log
        EstudioService.Estado estado = estudio.iniciar(pedido.texto(), revisao);
        log.info("cliente=central acao=estudio resultado=iniciado revisao={} letras={}", revisao, letras);
        return estado;
    }

    @GetMapping("/estudio")
    public EstudioService.Estado estado() {
        return estudio.estado();
    }
}
