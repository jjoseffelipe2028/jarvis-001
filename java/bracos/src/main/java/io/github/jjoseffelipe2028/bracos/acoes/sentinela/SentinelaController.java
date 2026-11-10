package io.github.jjoseffelipe2028.bracos.acoes.sentinela;

import io.github.jjoseffelipe2028.bracos.auditoria.Cliente;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * GET /acoes/sentinela/portas → quais portas do PC estão abertas para a rede (Ticket 021). 🛡️
 * GET /acoes/sentinela/integridade → alguém mexeu nas travas do projeto? (Ticket 022). 🔏
 * POST /acoes/sentinela/golpe → essa mensagem tem cara de golpe? (Ticket 023). 🎣
 * Nada é alterado no PC. O token (pedido assinado) já foi conferido antes de chegar aqui.
 */
@RestController
@RequestMapping("/acoes/sentinela")
public class SentinelaController {

    private static final Logger log = LoggerFactory.getLogger(SentinelaController.class);

    private final PortasService portas;
    private final IntegridadeService integridade;
    private final DetectorDeGolpe detector;

    public SentinelaController(PortasService portas, IntegridadeService integridade, DetectorDeGolpe detector) {
        this.portas = portas;
        this.integridade = integridade;
        this.detector = detector;
    }

    /** O texto colado NUNCA vai para o log: só o nível e quantos links. O link não é aberto. */
    @PostMapping("/golpe")
    public DetectorDeGolpe.Veredito golpe(@Valid @RequestBody GolpeRequest pedido,
            @RequestHeader(value = Cliente.CABECALHO, required = false) String clienteBruto) {
        DetectorDeGolpe.Veredito v = detector.analisar(pedido.texto());
        log.info("cliente={} acao=golpe resultado=ok nivel={} links={}", Cliente.de(clienteBruto), v.nivel(), v.links());
        return v;
    }

    @GetMapping("/portas")
    public PortasService.Relatorio portas(
            @RequestHeader(value = Cliente.CABECALHO, required = false) String clienteBruto) {
        String cliente = Cliente.de(clienteBruto);
        try {
            PortasService.Relatorio r = portas.vigiar();
            log.info("cliente={} acao=sentinela resultado=ok rede={} alertas={}", cliente, r.abertasParaARede(), r.alertas());
            return r;
        } catch (RuntimeException e) {
            log.warn("cliente={} acao=sentinela resultado=erro motivo={}", cliente, e.getClass().getSimpleName());
            throw e;
        }
    }

    @GetMapping("/integridade")
    public IntegridadeService.Relatorio integridade(
            @RequestHeader(value = Cliente.CABECALHO, required = false) String clienteBruto) {
        String cliente = Cliente.de(clienteBruto);
        try {
            IntegridadeService.Relatorio r = integridade.conferir();
            log.info("cliente={} acao=integridade resultado=ok alterados={}", cliente, r.alterados());
            return r;
        } catch (RuntimeException e) {
            log.warn("cliente={} acao=integridade resultado=erro motivo={}", cliente, e.getClass().getSimpleName());
            throw e;
        }
    }
}
