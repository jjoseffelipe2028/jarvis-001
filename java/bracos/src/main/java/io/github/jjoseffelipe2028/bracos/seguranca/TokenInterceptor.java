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
 * Porteiro de todo /acoes/**: exige um pedido ASSINADO (Ticket 007, ver Assinatura)
 * ou o cabeçalho X-Jarvis-Token (testes manuais com curl). 🔒
 * Evita CSRF (página maliciosa chamando a API) e adivinhação do token por tempo de resposta.
 * Bloqueio por padrão: só as rotas de ROTAS_PUBLICAS (leitura sem dado pessoal) passam sem token.
 */
public class TokenInterceptor implements HandlerInterceptor {

    public static final String CABECALHO = "X-Jarvis-Token";

    private static final java.util.Set<String> METODOS_SO_LEITURA = java.util.Set.of("GET", "HEAD", "OPTIONS");

    /** Únicas rotas abertas sem token. Comparação EXATA: qualquer variação cai no porteiro. */
    // /acoes/prova é pública de propósito: é o "aperto de mão" que acontece ANTES de o token sair do Python
    static final java.util.Set<String> ROTAS_PUBLICAS = java.util.Set.of("/acoes/hora", "/acoes/prova");

    private static final Logger log = LoggerFactory.getLogger(TokenInterceptor.class);

    private final byte[] hashEsperado; // null = token não configurado
    private final Assinatura assinatura; // null = token não configurado
    private final Isca isca; // Ticket 034: null = sem isca

    public TokenInterceptor(String tokenConfigurado) {
        this(tokenConfigurado, java.time.Clock.systemUTC(), null);
    }

    /** Ticket 034: com a isca (honeytoken). Ela só é olhada DEPOIS de o porteiro recusar o pedido. */
    public TokenInterceptor(String tokenConfigurado, Isca isca) {
        this(tokenConfigurado, java.time.Clock.systemUTC(), isca);
    }

    TokenInterceptor(String tokenConfigurado, java.time.Clock relogio) {
        this(tokenConfigurado, relogio, null);
    }

    TokenInterceptor(String tokenConfigurado, java.time.Clock relogio, Isca isca) {
        boolean semToken = tokenConfigurado == null || tokenConfigurado.isBlank();
        this.hashEsperado = semToken ? null : sha256(tokenConfigurado);
        this.assinatura = semToken ? null : new Assinatura(tokenConfigurado, relogio);
        this.isca = isca;
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

        // Jeito 1 (Ticket 007, o do Python): pedido ASSINADO. O token nunca viaja.
        if (request.getHeader(Assinatura.CAB_ASSINATURA) != null) {
            String rota = request.getRequestURI()
                    + (request.getQueryString() == null ? "" : "?" + request.getQueryString());
            byte[] corpo = CorpoGuardadoFilter.corpo(request);
            Assinatura.Resultado r = corpo == null
                    ? Assinatura.Resultado.FORMATO // o corpo não foi guardado: não dá para conferir → recusa
                    : assinatura.conferir(request.getMethod(), rota,
                            request.getHeader(Assinatura.CAB_HORA), request.getHeader(Assinatura.CAB_NONCE),
                            corpo, request.getHeader(Assinatura.CAB_ASSINATURA));
            if (r == Assinatura.Resultado.OK) {
                return true;
            }
            // Ticket 034: a assinatura não bate com o token de verdade. Bate com o token-ISCA? Então é alarme.
            if (corpo != null && isca != null && isca.assinouComAIsca(request.getMethod(), rota,
                    request.getHeader(Assinatura.CAB_HORA), request.getHeader(Assinatura.CAB_NONCE),
                    corpo, request.getHeader(Assinatura.CAB_ASSINATURA))) {
                alarmeDaIsca(cliente, request, response);
                return false;
            }
            // Motivo = nome fixo do enum (não vem de fora)
            log.warn("cliente={} acao={} resultado=negado motivo=assinatura_{}",
                    cliente, LogSeguro.limpar(request.getRequestURI()), r.name().toLowerCase(java.util.Locale.ROOT));
            responder(response, r == Assinatura.Resultado.LOTADO ? 503 : 401,
                    r == Assinatura.Resultado.LOTADO ? "tente de novo" : "nao autorizado");
            return false;
        }

        // Jeito 2 (para testes manuais com curl): o token no cabeçalho.
        // Fica registrado no log, para o José ver se algo além dos testes manuais usa este caminho.
        String recebido = request.getHeader(CABECALHO);
        if (recebido != null) {
            log.info("cliente={} acao={} aviso=token_simples", cliente, LogSeguro.limpar(request.getRequestURI()));
        }
        // Compara os hashes SHA-256 em tempo constante (esconde também o tamanho do token)
        boolean ok = recebido != null && MessageDigest.isEqual(hashEsperado, sha256(recebido));
        if (!ok && isca != null && isca.ehOTokenIsca(recebido)) {
            alarmeDaIsca(cliente, request, response); // Ticket 034
            return false;
        }
        if (!ok) {
            log.warn("cliente={} acao={} resultado=negado motivo=token_invalido",
                    cliente, LogSeguro.limpar(request.getRequestURI()));
            responder(response, 401, "nao autorizado");
            return false;
        }
        return true;
    }

    /** 🪤 Alguém usou o token-isca. Conta, grita no log e responde o MESMO 401 de sempre (não entrega a armadilha). */
    private void alarmeDaIsca(String cliente, HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        isca.tocar();
        log.error("cliente={} acao={} resultado=negado motivo=ISCA_TOCADA alarme=alguem_usou_o_token_isca",
                cliente, LogSeguro.limpar(request.getRequestURI()));
        responder(response, 401, "nao autorizado");
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
