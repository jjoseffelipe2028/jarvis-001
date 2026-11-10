package io.github.jjoseffelipe2028.bracos.central;

import java.util.Map;

import io.github.jjoseffelipe2028.bracos.auditoria.Cliente;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Anota no {@link QuadroDeEventos} as ações dos braços em /acoes/** (Ticket 017). 🔒
 * - NÃO decide nada: roda depois do pedido e apenas anota o resultado.
 * - Só as rotas da lista fixa {@link #ROTAS} viram evento (comparação EXATA).
 *   /acoes/hora fica de fora de propósito: a Central consulta a cada minuto e viraria barulho.
 * - Registrado ANTES dos porteiros: assim um pedido recusado também aparece (🚨 no escritório).
 */
public class EventosInterceptor implements HandlerInterceptor {

    static final Map<String, String> ROTAS = Map.of(
            "POST /acoes/daily", "daily",
            "GET /acoes/pendencias", "pendencias",
            "GET /acoes/git-status", "git-status",
            "POST /acoes/video/avaliar", "video-avaliar",
            "POST /acoes/video/relatorio", "video-relatorio");

    private final QuadroDeEventos quadro;

    public EventosInterceptor(QuadroDeEventos quadro) {
        this.quadro = quadro;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
                                Exception ex) {
        String acao = ROTAS.get(request.getMethod() + " " + request.getRequestURI());
        if (acao == null) {
            return;
        }
        quadro.registrar(acao, resultado(response.getStatus(), ex), Cliente.de(request.getHeader(Cliente.CABECALHO)));
    }

    static String resultado(int status, Exception ex) {
        if (ex != null || status >= 500 && status != 503) {
            return "erro";
        }
        if (status == 503) {
            return "indisponivel";
        }
        if (status == 401 || status == 403) {
            return "recusado";
        }
        return status >= 400 ? "invalido" : "ok";
    }
}
