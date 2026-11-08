package io.github.jjoseffelipe2028.bracos.seguranca;

import java.io.IOException;
import java.util.Locale;
import java.util.Set;

import io.github.jjoseffelipe2028.bracos.auditoria.Cliente;
import io.github.jjoseffelipe2028.bracos.auditoria.LogSeguro;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Só aceita pedidos endereçados a localhost / 127.0.0.1 (cabeçalho Host). 🔒
 * Contra DNS rebinding: um site malicioso (ex.: evil.com) faz o próprio nome apontar
 * para 127.0.0.1 e o navegador chama a API com "Host: evil.com". Aqui isso vira 403.
 * Vale para GET e POST em /acoes/**.
 */
public class HostInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(HostInterceptor.class);

    private static final Set<String> PERMITIDOS = Set.of("localhost", "127.0.0.1");

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        // getServerName() = nome que veio no cabeçalho Host (sem a porta)
        String host = request.getServerName();
        if (host != null && PERMITIDOS.contains(host.toLowerCase(Locale.ROOT))) {
            return true;
        }
        log.warn("cliente={} acao={} resultado=negado motivo=host_nao_permitido",
                Cliente.de(request.getHeader(Cliente.CABECALHO)), LogSeguro.limpar(request.getRequestURI()));
        response.setStatus(403);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"erro\":\"host nao permitido\"}");
        return false;
    }
}
