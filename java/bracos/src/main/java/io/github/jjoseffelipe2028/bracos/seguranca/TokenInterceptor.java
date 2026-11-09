package io.github.jjoseffelipe2028.bracos.seguranca;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import io.github.jjoseffelipe2028.bracos.auditoria.Cliente;
import io.github.jjoseffelipe2028.bracos.auditoria.LogSeguro;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Porteiro de todo /acoes/**: exige o cabeçalho X-Jarvis-Token. 🔒
 * Evita CSRF (página maliciosa chamando a API) e adivinhação do token por tempo de resposta.
 * Bloqueio por padrão: só as rotas de ROTAS_PUBLICAS (leitura sem dado pessoal) passam sem token.
 */
public class TokenInterceptor implements HandlerInterceptor {

    public static final String CABECALHO = "X-Jarvis-Token";

    private static final java.util.Set<String> METODOS_SO_LEITURA = java.util.Set.of("GET", "HEAD", "OPTIONS");

    /** Únicas rotas abertas sem token. Comparação EXATA: qualquer variação cai no porteiro. */
    static final java.util.Set<String> ROTAS_PUBLICAS = java.util.Set.of("/acoes/hora");

    private static final Logger log = LoggerFactory.getLogger(TokenInterceptor.class);

    private final byte[] hashEsperado; // null = token não configurado

    public TokenInterceptor(String tokenConfigurado) {
        this.hashEsperado = (tokenConfigurado == null || tokenConfigurado.isBlank())
                ? null
                : sha256(tokenConfigurado);
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        // Só LEITURA em rota PÚBLICA passa sem token (ex.: GET /acoes/hora).
        // Todo o resto (POST, PUT, DELETE e até GET com dado pessoal, como /acoes/pendencias): porteiro.
        // Ação nova já nasce protegida.
        boolean leitura = METODOS_SO_LEITURA.contains(request.getMethod().toUpperCase(java.util.Locale.ROOT));
        if (leitura && ROTAS_PUBLICAS.contains(request.getRequestURI())) {
            return true;
        }
        String cliente = Cliente.de(request.getHeader(Cliente.CABECALHO));

        // 503 primeiro: sem token configurado, ninguém passa
        if (hashEsperado == null) {
            log.warn("cliente={} acao={} resultado=negado motivo=token_nao_configurado",
                    cliente, LogSeguro.limpar(request.getRequestURI()));
            responder(response, 503, "token nao configurado");
            return false;
        }

        String recebido = request.getHeader(CABECALHO);
        // Compara os hashes SHA-256 em tempo constante (esconde também o tamanho do token)
        boolean ok = recebido != null && MessageDigest.isEqual(hashEsperado, sha256(recebido));
        if (!ok) {
            log.warn("cliente={} acao={} resultado=negado motivo=token_invalido",
                    cliente, LogSeguro.limpar(request.getRequestURI()));
            responder(response, 401, "nao autorizado");
            return false;
        }
        return true;
    }

    private static byte[] sha256(String texto) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(texto.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponivel", e);
        }
    }

    private static void responder(HttpServletResponse response, int status, String mensagem) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"erro\":\"" + mensagem + "\"}");
    }
}
