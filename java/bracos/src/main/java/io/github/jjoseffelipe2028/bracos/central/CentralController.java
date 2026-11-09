package io.github.jjoseffelipe2028.bracos.central;

import java.util.Map;

import io.github.jjoseffelipe2028.bracos.acoes.git.GitStatusService;
import io.github.jjoseffelipe2028.bracos.acoes.pendencias.PendenciasService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Rotas da Central v1 (Ticket 015). Os porteiros (Host + CentralInterceptor) rodam antes. 🔒
 * - POST /central/sessao: troca a chave de uso único por um crachá (cookie HttpOnly).
 * - GET  /central/acoes/pendencias e /central/acoes/git-status: as MESMAS ações dos braços,
 *   só de LEITURA. Nada que escreve ou executa entra na Central sem ticket próprio.
 */
@RestController
@RequestMapping("/central")
public class CentralController {

    private static final Logger log = LoggerFactory.getLogger(CentralController.class);

    private final ChaveDaCentral chave;
    private final SessoesDaCentral sessoes;
    private final PendenciasService pendencias;
    private final GitStatusService git;

    public CentralController(ChaveDaCentral chave, SessoesDaCentral sessoes,
                             PendenciasService pendencias, GitStatusService git) {
        this.chave = chave;
        this.sessoes = sessoes;
        this.pendencias = pendencias;
        this.git = git;
    }

    /** Corpo: {"chave":"<64 hex>"}. Qualquer erro → 401 (sem dizer o motivo). */
    @PostMapping("/sessao")
    public ResponseEntity<Map<String, String>> sessao(@RequestBody(required = false) Map<String, Object> corpo) {
        if (!chave.ligada()) {
            // Falha fechada: sem arquivo de chave configurado, ninguém ganha crachá
            log.warn("cliente=central acao=sessao resultado=negado motivo=chave_desligada");
            return ResponseEntity.status(401).body(Map.of("erro", "nao autorizado"));
        }
        Object recebida = corpo == null ? null : corpo.get("chave");
        if (!(recebida instanceof String texto) || !chave.usar(texto)) {
            log.warn("cliente=central acao=sessao resultado=negado motivo=chave_invalida");
            return ResponseEntity.status(401).body(Map.of("erro", "nao autorizado"));
        }
        ResponseCookie cracha = ResponseCookie.from(CentralInterceptor.COOKIE, sessoes.criar())
                .httpOnly(true)          // o JavaScript não lê
                .sameSite("Strict")      // site de fora não faz o navegador enviar
                .path("/central")        // só vai para as rotas da Central
                .maxAge(SessoesDaCentral.VALIDADE)
                .build();
        log.info("cliente=central acao=sessao resultado=ok");
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, cracha.toString())
                .body(Map.of("resultado", "ok"));
    }

    @GetMapping("/acoes/pendencias")
    public PendenciasService.Resultado pendencias() {
        PendenciasService.Resultado r = pendencias.listar();
        log.info("cliente=central acao=pendencias resultado=ok total={}", r.total());
        return r;
    }

    @GetMapping("/acoes/git-status")
    public GitStatusService.Status gitStatus() {
        GitStatusService.Status s = git.consultar();
        log.info("cliente=central acao=git-status resultado=ok limpo={}", s.limpo());
        return s;
    }
}
