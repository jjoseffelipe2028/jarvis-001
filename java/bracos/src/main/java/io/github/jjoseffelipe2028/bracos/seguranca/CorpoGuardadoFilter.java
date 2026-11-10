package io.github.jjoseffelipe2028.bracos.seguranca;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Guarda o corpo do pedido em /acoes/** e /central/** (até 64 KB) para a assinatura poder conferir os bytes
 * EXATOS que chegaram, e o controller ainda conseguir ler o JSON depois. 🔒
 * Corpo maior que 64 KB → 413, antes de qualquer outra coisa (anti DoS de memória).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class CorpoGuardadoFilter extends OncePerRequestFilter {

    static final int LIMITE_CORPO = 64 * 1024;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // Ticket 019: a Central (/central/**) também tem limite (achado 2 da revisão de 10/11)
        String rota = request.getRequestURI();
        return !(rota.startsWith("/acoes/") || rota.startsWith("/central/"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        byte[] corpo = request.getInputStream().readNBytes(LIMITE_CORPO + 1);
        if (corpo.length > LIMITE_CORPO) {
            response.setStatus(413);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"erro\":\"pedido grande demais\"}");
            return;
        }
        chain.doFilter(new Guardado(request, corpo), response);
    }

    /**
     * O corpo guardado (bytes exatos), ou null se o pedido NÃO passou por este filtro.
     * null = o porteiro recusa a assinatura (falha fechada): nunca conferir um corpo "vazio"
     * enquanto o controller lê outro.
     */
    public static byte[] corpo(HttpServletRequest request) {
        HttpServletRequest atual = request;
        while (true) {
            if (atual instanceof Guardado g) {
                return g.corpo;
            }
            if (!(atual instanceof HttpServletRequestWrapper w)) {
                return null;
            }
            atual = (HttpServletRequest) w.getRequest();
        }
    }

    static final class Guardado extends HttpServletRequestWrapper {
        private final byte[] corpo;

        Guardado(HttpServletRequest request, byte[] corpo) {
            super(request);
            this.corpo = corpo;
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream entrada = new ByteArrayInputStream(corpo);
            return new ServletInputStream() {
                @Override public int read() { return entrada.read(); }
                @Override public int read(byte[] b, int off, int len) { return entrada.read(b, off, len); }
                @Override public boolean isFinished() { return entrada.available() == 0; }
                @Override public boolean isReady() { return true; }
                @Override public void setReadListener(ReadListener l) { throw new UnsupportedOperationException(); }
            };
        }

        @Override
        public BufferedReader getReader() {
            return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
        }
    }
}
