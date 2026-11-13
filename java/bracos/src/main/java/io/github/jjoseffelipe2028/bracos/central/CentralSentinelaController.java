package io.github.jjoseffelipe2028.bracos.central;

import io.github.jjoseffelipe2028.bracos.acoes.sentinela.DetectorDeGolpe;
import io.github.jjoseffelipe2028.bracos.acoes.sentinela.GolpeRequest;
import io.github.jjoseffelipe2028.bracos.acoes.sentinela.IntegridadeService;
import io.github.jjoseffelipe2028.bracos.acoes.sentinela.PortasService;
import io.github.jjoseffelipe2028.bracos.acoes.sentinela.PropostaRequest;
import io.github.jjoseffelipe2028.bracos.acoes.sentinela.VerificadorDePropostas;
import io.github.jjoseffelipe2028.bracos.seguranca.Isca;
import org.springframework.beans.factory.ObjectProvider;
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
 * Ticket 039 (página Cyber): POST /proposta (verificador de propostas) e GET /isca (a isca foi tocada?).
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
    private final VerificadorDePropostas verificador;
    private final ObjectProvider<Isca> isca;

    public CentralSentinelaController(PortasService portas, IntegridadeService integridade, DetectorDeGolpe detector,
            VerificadorDePropostas verificador, ObjectProvider<Isca> isca) {
        this.portas = portas;
        this.integridade = integridade;
        this.detector = detector;
        this.verificador = verificador;
        this.isca = isca;
    }

    /** Ticket 039: o mesmo verificador da voz (Ticket 030). Texto, remetente e marca não vão para o log. */
    @PostMapping("/proposta")
    public VerificadorDePropostas.Parecer proposta(@Valid @RequestBody PropostaRequest pedido) {
        VerificadorDePropostas.Parecer p = verificador.analisar(pedido.texto(), pedido.remetente(), pedido.marca());
        log.info("cliente=central acao=proposta resultado=ok nivel={} links={}", p.nivel(), p.links());
        return p;
    }

    /** Ticket 039: estado da isca (Ticket 034). Sem isca configurada → "desligada". */
    @GetMapping("/isca")
    public Isca.Estado isca() {
        Isca armadilha = isca.getIfAvailable();
        Isca.Estado e = armadilha == null ? new Isca.Estado(false, 0, "", "sem") : armadilha.estado();
        log.info("cliente=central acao=isca resultado=ok ligada={} toques={}", e.ligada(), e.toques());
        return e;
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
