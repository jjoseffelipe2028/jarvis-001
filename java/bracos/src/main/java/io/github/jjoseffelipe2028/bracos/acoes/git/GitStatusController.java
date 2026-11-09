package io.github.jjoseffelipe2028.bracos.acoes.git;

import io.github.jjoseffelipe2028.bracos.auditoria.Cliente;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * GET /acoes/git-status → tem algo sem commit? 🔒 (token exigido pelo TokenInterceptor)
 * Exemplo: {"branch":"main","limpo":false,"alterados":2,"novos":1,"aFrente":0,"atras":0}
 */
@RestController
@RequestMapping("/acoes")
public class GitStatusController {

    private static final Logger log = LoggerFactory.getLogger(GitStatusController.class);

    private final GitStatusService service;

    public GitStatusController(GitStatusService service) {
        this.service = service;
    }

    @GetMapping("/git-status")
    public GitStatusService.Status gitStatus(
            @RequestHeader(value = Cliente.CABECALHO, required = false) String clienteBruto) {

        String cliente = Cliente.de(clienteBruto);
        try {
            GitStatusService.Status status = service.consultar();
            // Só números no log: nunca nome de arquivo nem caminho
            log.info("cliente={} acao=git-status resultado=ok limpo={} alterados={} novos={}",
                    cliente, status.limpo(), status.alterados(), status.novos());
            return status;
        } catch (RuntimeException e) {
            log.warn("cliente={} acao=git-status resultado=erro motivo={}", cliente, e.getClass().getSimpleName());
            throw e;
        }
    }
}
