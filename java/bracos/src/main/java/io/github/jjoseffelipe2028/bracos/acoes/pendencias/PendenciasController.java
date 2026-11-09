package io.github.jjoseffelipe2028.bracos.acoes.pendencias;

import io.github.jjoseffelipe2028.bracos.auditoria.Cliente;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * GET /acoes/pendencias → tarefas abertas dos últimos 7 dailys. 🔒
 * Mesmo sendo leitura, EXIGE token (dado pessoal): o TokenInterceptor confere antes.
 * Exemplo: {"pendencias":[{"dia":"2026-11-08","texto":"estudar Git"}],"total":1,"ignorados":0}
 */
@RestController
@RequestMapping("/acoes")
public class PendenciasController {

    private static final Logger log = LoggerFactory.getLogger(PendenciasController.class);

    private final PendenciasService service;

    public PendenciasController(PendenciasService service) {
        this.service = service;
    }

    @GetMapping("/pendencias")
    public PendenciasService.Resultado pendencias(
            @RequestHeader(value = Cliente.CABECALHO, required = false) String clienteBruto) {

        String cliente = Cliente.de(clienteBruto); // só python/central/desconhecido
        try {
            PendenciasService.Resultado resultado = service.listar();
            // Só números no log: nunca o texto das tarefas nem o caminho
            log.info("cliente={} acao=pendencias resultado=ok total={} ignorados={}",
                    cliente, resultado.total(), resultado.ignorados());
            return resultado;
        } catch (RuntimeException e) {
            log.warn("cliente={} acao=pendencias resultado=erro motivo={}", cliente, e.getClass().getSimpleName());
            throw e;
        }
    }
}
