package io.github.jjoseffelipe2028.bracos.central;

import java.io.IOException;

import io.github.jjoseffelipe2028.bracos.auditoria.LogSeguro;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Porteiro da Central em /central/** (Ticket 015). Roda DEPOIS do HostInterceptor. 🔒
 * 1) Cabeçalho X-Jarvis-Central: 1 obrigatório. Um site de fora que tentar mandar isso
 *    cai no "preflight" de CORS, e a API não libera CORS: o navegador barra antes.
 * 2) Se vier Origin, tem que ser a própria Central (http://127.0.0.1:porta ou localhost).
 *    Na ESCRITA (POST em /central/acoes/**, Ticket 020) o Origin é obrigatório.
 * 3) Se vier Sec-Fetch-Site, tem que ser "same-origin".
 * 4) Em /central/acoes/**: crachá (cookie) válido.
 * Criado pelo WebConfig (não é @Component), só quando a Central existe.
 */
public class CentralInterceptor implements HandlerInterceptor {

    public static final String CABECALHO = "X-Jarvis-Central";
    public static final String COOKIE = "JARVIS_CENTRAL";

    private static final Logger log = LoggerFactory.getLogger(CentralInterceptor.class);

    private final SessoesDaCentral sessoes;

    public CentralInterceptor(SessoesDaCentral sessoes) {
        this.sessoes = sessoes;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        String rota = request.getRequestURI();

        if (!"1".equals(request.getHeader(CABECALHO))) {
            return negar(response, rota, 401, "sem_cabecalho_central");
        }
        String origem = request.getHeader("Origin");
        if (origem != null && !origemPropria(origem, request)) {
            return negar(response, rota, 403, "origem_estranha");
        }
        if (origem == null && escrita(request) && rota.startsWith("/central/acoes/")) {
            // Ticket 020: ação que ESCREVE exige Origin. O navegador sempre manda Origin num POST;
            // pedido sem ele não veio da Central (ex.: script de fora tentando escrever no cofre).
            return negar(response, rota, 403, "escrita_sem_origem");
        }
        String site = request.getHeader("Sec-Fetch-Site");
        if (site != null && !"same-origin".equals(site)) {
            return negar(response, rota, 403, "pedido_de_outro_site");
        }
        if (rota.startsWith("/central/acoes/") && !sessoes.valido(cracha(request))) {
            return negar(response, rota, 401, "sem_cracha");
        }
        return true;
    }

    /** Tudo que não é só leitura (GET/HEAD) conta como escrita. */
    static boolean escrita(HttpServletRequest request) {
        String metodo = request.getMethod();
        return !"GET".equals(metodo) && !"HEAD".equals(metodo);
    }

    private static boolean origemPropria(String origem, HttpServletRequest request) {
        int porta = request.getServerPort();
        return origem.equals("http://127.0.0.1:" + porta) || origem.equals("http://localhost:" + porta);
    }

    private static String cracha(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie c : cookies) {
            if (COOKIE.equals(c.getName())) {
                return c.getValue();
            }
        }
        return null;
    }

    private static boolean negar(HttpServletResponse response, String rota, int status, String motivo)
            throws IOException {
        log.warn("cliente=central acao={} resultado=negado motivo={}", LogSeguro.limpar(rota), motivo);
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(status == 401 ? "{\"erro\":\"nao autorizado\"}" : "{\"erro\":\"proibido\"}");
        return false;
    }
}
