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

    static final Map<String, String> ROTAS = Map.ofEntries(
            Map.entry("POST /acoes/daily", "daily"),
            Map.entry("GET /acoes/pendencias", "pendencias"),
            Map.entry("GET /acoes/git-status", "git-status"),
            Map.entry("POST /acoes/video/avaliar", "video-avaliar"),
            Map.entry("POST /acoes/video/relatorio", "video-relatorio"),
            Map.entry("POST /central/acoes/daily", "daily"), // Ticket 020: daily pela Central
            Map.entry("GET /acoes/sentinela/portas", "sentinela"), // Ticket 021: vigia de portas pela voz
            Map.entry("GET /acoes/sentinela/integridade", "integridade"), // Ticket 022: vigia de integridade
            Map.entry("POST /acoes/sentinela/golpe", "golpe"), // Ticket 023: detector de golpe
            Map.entry("POST /acoes/sentinela/proposta", "proposta"), // Ticket 030: verificador de propostas
            Map.entry("GET /acoes/sentinela/isca", "isca"), // Ticket 034: a isca foi tocada?
            Map.entry("POST /central/acoes/sentinela/golpe", "golpe"));

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
        quadro.registrar(acao, resultado(response.getStatus(), ex), cliente(request));
    }

    /** Rota da Central é sempre "central"; nas outras, o cabeçalho (só para o log, nunca para decidir). */
    static String cliente(HttpServletRequest request) {
        if (request.getRequestURI().startsWith("/central/")) {
            return "central";
        }
        return Cliente.de(request.getHeader(Cliente.CABECALHO));
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
