package io.github.jjoseffelipe2028.bracos.acoes.treino;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import io.github.jjoseffelipe2028.bracos.central.CentralInterceptor;
import io.github.jjoseffelipe2028.bracos.central.SessoesDaCentral;
import io.github.jjoseffelipe2028.bracos.seguranca.HostInterceptor;
import io.github.jjoseffelipe2028.bracos.seguranca.Isca;
import io.github.jjoseffelipe2028.bracos.seguranca.TokenInterceptor;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Sala de Treino Cyber (Ticket 064): "Simular ataque" e ver a defesa agir. 🥋🔒
 *
 * - NADA vai pela rede. O ataque é um pedido de MENTIRA montado em memória (um wrapper do pedido real,
 *   com Host/Origin/cookie/token trocados) que passa pelos MESMOS porteiros de verdade, na mesma ordem do WebConfig.
 * - Só o {@code preHandle} dos porteiros roda. O controller de destino NUNCA é chamado:
 *   mesmo que um porteiro falhe, nada é escrito. Aí a resposta diz "defesa falhou" (e o teste quebra).
 * - A resposta de verdade não é tocada: o porteiro escreve num "papel de rascunho" (wrapper da resposta).
 * - Os 4 ataques são uma lista fechada (enum). Nada do ataque vem da tela, só o nome.
 */
@Service
public class TreinoService {

    /** A lista fechada de ataques. */
    public enum Ataque {
        CSRF("POST", "/central/acoes/daily", "127.0.0.1",
                Map.of("Origin", "https://site-malicioso.example", "Sec-Fetch-Site", "cross-site"), true,
                "Um site de fora tenta usar o seu navegador (que tem o crachá) para escrever no daily."),
        REBINDING("GET", "/central/acoes/pendencias", "evil.com",
                Map.of(CentralInterceptor.CABECALHO, "1"), false,
                "Um domínio malicioso passa a apontar para 127.0.0.1 e o navegador chama a API com Host: evil.com."),
        SEM_CRACHA("GET", "/central/acoes/pendencias", "127.0.0.1",
                Map.of(CentralInterceptor.CABECALHO, "1"), false,
                "Um programa no PC tenta ler as suas pendências pela Central sem o crachá da sessão."),
        TOKEN_FALSO("POST", "/acoes/daily", "127.0.0.1",
                Map.of(TokenInterceptor.CABECALHO, "token-falso-do-treino"), false,
                "Alguém chuta um token secreto para escrever no daily pela API dos braços.");

        final String metodo;
        final String rota;
        final String host;
        final Map<String, String> cabecalhos;
        final boolean levaCracha;
        final String cenario;

        Ataque(String metodo, String rota, String host, Map<String, String> cabecalhos, boolean levaCracha, String cenario) {
            this.metodo = metodo;
            this.rota = rota;
            this.host = host;
            this.cabecalhos = cabecalhos;
            this.levaCracha = levaCracha;
            this.cenario = cenario;
        }

        /** "csrf" → CSRF. Fora da lista → vazio (a rota responde 400). */
        public static java.util.Optional<Ataque> de(String nome) {
            if (nome == null) {
                return java.util.Optional.empty();
            }
            for (Ataque a : values()) {
                if (a.name().toLowerCase(Locale.ROOT).replace('_', '-').equals(nome)) {
                    return java.util.Optional.of(a);
                }
            }
            return java.util.Optional.empty();
        }
    }

    /** O que a tela vê: textos fixos daqui + o nome do porteiro e o status. */
    public record Resultado(String ataque, String cenario, String alvo, boolean barrado, String porteiro, int status,
                            String explicacao) {
    }

    private static final Map<String, String> POR_QUE = Map.of(
            "HostInterceptor", "O porteiro de Host só aceita localhost e 127.0.0.1: o nome falso não entra (anti DNS rebinding).",
            "CentralInterceptor", "O porteiro da Central exige o cabeçalho próprio, Origin da própria Central e o crachá da sessão.",
            "TokenInterceptor", "O porteiro dos braços compara o hash do token em tempo constante: token errado = 401.");

    private final String token;
    private final SessoesDaCentral sessoes;
    private final ObjectProvider<Isca> isca;

    public TreinoService(@Value("${jarvis.token:}") String token, SessoesDaCentral sessoes, ObjectProvider<Isca> isca) {
        this.token = token;
        this.sessoes = sessoes;
        this.isca = isca;
    }

    /** Os porteiros na MESMA ordem do WebConfig (Host primeiro, depois o da área). */
    List<HandlerInterceptor> porteiros(String rota) {
        if (rota.startsWith("/central/")) {
            return List.of(new HostInterceptor(), new CentralInterceptor(sessoes));
        }
        return List.of(new HostInterceptor(), new TokenInterceptor(token, isca.getIfAvailable()));
    }

    public Resultado simular(Ataque ataque, HttpServletRequest real) {
        HttpServletRequest falso = new PedidoFalso(real, ataque);
        for (HandlerInterceptor porteiro : porteiros(ataque.rota)) {
            Rascunho resposta = new Rascunho(respostaVazia());
            boolean passou;
            try {
                passou = porteiro.preHandle(falso, resposta, null);
            } catch (Exception e) {
                passou = false; // porteiro que explode também não deixa passar
            }
            if (!passou) {
                String nome = porteiro.getClass().getSimpleName();
                return new Resultado(nomeDe(ataque), ataque.cenario, ataque.metodo + " " + ataque.rota, true, nome,
                        resposta.status, POR_QUE.getOrDefault(nome, "Barrado."));
            }
        }
        // Todos deixaram passar. O controller NÃO roda; só avisamos.
        return new Resultado(nomeDe(ataque), ataque.cenario, ataque.metodo + " " + ataque.rota, false, "nenhum", 200,
                "🚨 Nenhum porteiro barrou este ataque. A defesa precisa de conserto antes de usar o Jarvis.");
    }

    static String nomeDe(Ataque a) {
        return a.name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    /** O pedido de mentira: só o que o ataque define. Nada do pedido real "vaza" (menos o crachá, no CSRF). */
    static final class PedidoFalso extends HttpServletRequestWrapper {
        private final Ataque ataque;
        private final Cookie[] cookies;

        PedidoFalso(HttpServletRequest real, Ataque ataque) {
            super(real);
            this.ataque = ataque;
            Cookie cracha = null;
            if (ataque.levaCracha && real.getCookies() != null) {
                for (Cookie c : real.getCookies()) {
                    if (CentralInterceptor.COOKIE.equals(c.getName())) {
                        cracha = c; // CSRF: o navegador da vítima manda o crachá junto
                    }
                }
            }
            this.cookies = cracha == null ? null : new Cookie[] {cracha};
        }

        @Override public String getMethod() { return ataque.metodo; }
        @Override public String getRequestURI() { return ataque.rota; }
        @Override public String getQueryString() { return null; }
        @Override public String getServerName() { return ataque.host; }
        @Override public int getServerPort() { return super.getServerPort(); }
        @Override public Cookie[] getCookies() { return cookies; }
        @Override public String getHeader(String nome) {
            for (Map.Entry<String, String> e : ataque.cabecalhos.entrySet()) {
                if (e.getKey().equalsIgnoreCase(nome)) {
                    return e.getValue();
                }
            }
            return "Host".equalsIgnoreCase(nome) ? ataque.host : null;
        }
        @Override public Enumeration<String> getHeaders(String nome) {
            String v = getHeader(nome);
            return v == null ? Collections.emptyEnumeration() : Collections.enumeration(List.of(v));
        }
        @Override public Enumeration<String> getHeaderNames() {
            return Collections.enumeration(ataque.cabecalhos.keySet());
        }
    }

    /** Uma resposta "de papel": qualquer método não faz nada (devolve o valor padrão). Nunca é a resposta real. */
    static HttpServletResponse respostaVazia() {
        return (HttpServletResponse) java.lang.reflect.Proxy.newProxyInstance(TreinoService.class.getClassLoader(),
                new Class<?>[] {HttpServletResponse.class}, (proxy, metodo, args) -> {
                    Class<?> tipo = metodo.getReturnType();
                    if (tipo == boolean.class) {
                        return false;
                    }
                    if (tipo == int.class) {
                        return 0;
                    }
                    if (tipo == long.class) {
                        return 0L;
                    }
                    return null;
                });
    }

    /** O "papel de rascunho": guarda o status e o texto do porteiro sem tocar na resposta de verdade. */
    static final class Rascunho extends HttpServletResponseWrapper {
        int status = 200;
        private final PrintWriter escritor = new PrintWriter(new StringWriter());

        Rascunho(HttpServletResponse base) {
            super(base);
        }

        @Override public void setStatus(int sc) { status = sc; }
        @Override public void sendError(int sc) { status = sc; }
        @Override public void sendError(int sc, String msg) { status = sc; }
        @Override public void setContentType(String type) { }
        @Override public void setHeader(String name, String value) { }
        @Override public void addHeader(String name, String value) { }
        @Override public PrintWriter getWriter() throws IOException { return escritor; }
    }
}
