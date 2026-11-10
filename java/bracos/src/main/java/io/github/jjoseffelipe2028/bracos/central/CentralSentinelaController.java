package io.github.jjoseffelipe2028.bracos.central;

import io.github.jjoseffelipe2028.bracos.acoes.sentinela.DetectorDeGolpe;
import io.github.jjoseffelipe2028.bracos.acoes.sentinela.GolpeRequest;
import io.github.jjoseffelipe2028.bracos.acoes.sentinela.IntegridadeService;
import io.github.jjoseffelipe2028.bracos.acoes.sentinela.PortasService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * GET /central/acoes/sentinela/portas (Ticket 021) e /integridade (Ticket 022): a Sentinela pela Central. 🛡️
 * POST /central/acoes/sentinela/golpe (Ticket 023): cola a mensagem e vê se tem cara de golpe. 🎣
 * Nada é alterado no PC. Os porteiros da Central (Host, cabeçalho, Origin e crachá) rodam antes;
 * por ser POST, o Origin é obrigatório.
 */
@RestController
@RequestMapping("/central/acoes/sentinela")
public class CentralSentinelaController {

    private static final Logger log = LoggerFactory.getLogger(CentralSentinelaController.class);

    private final PortasService portas;
    private final IntegridadeService integridade;
    private final DetectorDeGolpe detector;

    public CentralSentinelaController(PortasService portas, IntegridadeService integridade, DetectorDeGolpe detector) {
        this.portas = portas;
        this.integridade = integridade;
        this.detector = detector;
    }

    @PostMapping("/golpe")
    public DetectorDeGolpe.Veredito golpe(@Valid @RequestBody GolpeRequest pedido) {
        DetectorDeGolpe.Veredito v = detector.analisar(pedido.texto());
        log.info("cliente=central acao=golpe resultado=ok nivel={} links={}", v.nivel(), v.links());
        return v;
    }

    @GetMapping("/portas")
    public PortasService.Relatorio portas() {
        try {
            PortasService.Relatorio r = portas.vigiar();
            log.info("cliente=central acao=sentinela resultado=ok rede={} alertas={}", r.abertasParaARede(), r.alertas());
            return r;
        } catch (RuntimeException e) {
            log.warn("cliente=central acao=sentinela resultado=erro motivo={}", e.getClass().getSimpleName());
            throw e;
        }
    }

    @GetMapping("/integridade")
    public IntegridadeService.Relatorio integridade() {
        try {
            IntegridadeService.Relatorio r = integridade.conferir();
            log.info("cliente=central acao=integridade resultado=ok alterados={}", r.alterados());
            return r;
        } catch (RuntimeException e) {
            log.warn("cliente=central acao=integridade resultado=erro motivo={}", e.getClass().getSimpleName());
            throw e;
        }
    }
}
