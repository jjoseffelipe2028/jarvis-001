package io.github.jjoseffelipe2028.bracos.central;

import io.github.jjoseffelipe2028.bracos.acoes.treino.TreinoRequest;
import io.github.jjoseffelipe2028.bracos.acoes.treino.TreinoService;
import io.github.jjoseffelipe2028.bracos.erros.EntradaInvalidaException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * POST /central/acoes/treino (Ticket 064): Sala de Treino Cyber. 🥋🔒
 * - Só o nome do ataque vem da tela (lista fechada). Nada vai pela rede, nada é escrito.
 * - Log: ataque, se foi barrado e por qual porteiro. Se a defesa falhar, o log grita.
 */
@RestController
@RequestMapping("/central/acoes")
public class CentralTreinoController {

    private static final Logger log = LoggerFactory.getLogger(CentralTreinoController.class);

    private final TreinoService treino;

    public CentralTreinoController(TreinoService treino) {
        this.treino = treino;
    }

    @PostMapping("/treino")
    public TreinoService.Resultado treino(@Valid @RequestBody TreinoRequest pedido, HttpServletRequest real) {
        TreinoService.Ataque ataque = TreinoService.Ataque.de(pedido.ataque())
                .orElseThrow(() -> new EntradaInvalidaException("ataque desconhecido"));
        log.info("cliente=central acao=treino aviso=simulacao_comeca ataque={} (os 'negado' a seguir sao do treino)",
                pedido.ataque());
        TreinoService.Resultado r = treino.simular(ataque, real);
        if (r.barrado()) {
            log.info("cliente=central acao=treino ataque={} resultado=barrado porteiro={} status={}",
                    r.ataque(), r.porteiro(), r.status());
        } else {
            log.error("cliente=central acao=treino ataque={} resultado=defesa-falhou alarme=porteiro_deixou_passar",
                    r.ataque());
        }
        return r;
    }
}
