package io.github.jjoseffelipe2028.bracos.seguranca;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Cabeçalhos de segurança em TODA resposta (Ticket 019). 🔒
 * Achado 1 da revisão de 10/11: sem eles, outro site podia pôr a Central num iframe (clickjacking).
 * A CSP por {@code <meta>} da página não cobre {@code frame-ancestors}: precisa vir como cabeçalho.
 * - Roda primeiro de todos: até uma resposta 401, 403 ou 413 sai com os cabeçalhos.
 * - Rotas com dado pessoal (ações) ainda ganham {@code Cache-Control: no-store}.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CabecalhosSegurancaFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        response.setHeader("X-Frame-Options", "DENY");
        response.setHeader("Content-Security-Policy", "frame-ancestors 'none'");
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("Referrer-Policy", "no-referrer");
        if (temDado(request.getRequestURI())) {
            response.setHeader("Cache-Control", "no-store");
        }
        chain.doFilter(request, response);
    }

    /** Ações dos braços e da Central: a resposta pode ter nota ou pendência, então não fica em cache. */
    static boolean temDado(String rota) {
        return rota != null && (rota.startsWith("/acoes/") || rota.startsWith("/central/"));
    }
}
