package io.github.jjoseffelipe2028.bracos.acoes.treino;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import io.github.jjoseffelipe2028.bracos.central.CentralInterceptor;
import io.github.jjoseffelipe2028.bracos.central.SessoesDaCentral;
import io.github.jjoseffelipe2028.bracos.seguranca.Isca;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Ticket 064: os 4 ataques passam pelos porteiros DE VERDADE e têm que ser barrados. 🥋
 * Vira um "duelo automático": se alguém afrouxar um porteiro, este teste quebra no ./mvnw test.
 */
class TreinoServiceTest {

    private final SessoesDaCentral sessoes = new SessoesDaCentral();

    private TreinoService servico() {
        return new TreinoService("token-de-teste", sessoes, new StaticListableBeanFactory().getBeanProvider(Isca.class));
    }

    /** O pedido real que chega na rota de treino: vem da Central, com crachá válido. */
    private MockHttpServletRequest pedidoReal() {
        MockHttpServletRequest real = new MockHttpServletRequest("POST", "/central/acoes/treino");
        real.setServerName("127.0.0.1");
        real.setServerPort(8080);
        real.setCookies(new Cookie(CentralInterceptor.COOKIE, sessoes.criar()));
        return real;
    }

    @Test
    void osQuatroAtaques_saoBarrados_pelosPorteirosCertos() {
        TreinoService s = servico();
        MockHttpServletRequest real = pedidoReal();
        assertBarrado(s.simular(TreinoService.Ataque.CSRF, real), "CentralInterceptor");
        assertBarrado(s.simular(TreinoService.Ataque.REBINDING, real), "HostInterceptor");
        assertBarrado(s.simular(TreinoService.Ataque.SEM_CRACHA, real), "CentralInterceptor");
        assertBarrado(s.simular(TreinoService.Ataque.TOKEN_FALSO, real), "TokenInterceptor");
    }

    private static void assertBarrado(TreinoService.Resultado r, String porteiro) {
        assertTrue(r.barrado(), r.ataque() + " devia ser barrado");
        assertEquals(porteiro, r.porteiro(), r.ataque());
        assertTrue(r.status() == 401 || r.status() == 403, r.ataque() + " status " + r.status());
    }

    @Test
    void controle_umPedidoLegitimoPassaPelosMesmosPorteiros() throws Exception {
        // Prova de que o "barrado" acima não é defeito do pedido falso: o pedido certinho passa.
        MockHttpServletRequest legitimo = new MockHttpServletRequest("GET", "/central/acoes/pendencias");
        legitimo.setServerName("127.0.0.1");
        legitimo.setServerPort(8080);
        legitimo.addHeader(CentralInterceptor.CABECALHO, "1");
        legitimo.setCookies(new Cookie(CentralInterceptor.COOKIE, sessoes.criar()));
        for (HandlerInterceptor p : servico().porteiros("/central/acoes/pendencias")) {
            assertTrue(p.preHandle(legitimo, new MockHttpServletResponse(), null), p.getClass().getSimpleName());
        }
    }

    @Test
    void porteiroAfrouxado_eDetectado_eNadaRoda() {
        TreinoService frouxo = new TreinoService("token-de-teste", sessoes, new StaticListableBeanFactory().getBeanProvider(Isca.class)) {
            @Override
            List<HandlerInterceptor> porteiros(String rota) {
                return List.of(new HandlerInterceptor() {
                    @Override
                    public boolean preHandle(HttpServletRequest q, HttpServletResponse r, Object h) {
                        return true; // alguém "consertou" o porteiro deixando tudo passar
                    }
                });
            }
        };
        TreinoService.Resultado r = frouxo.simular(TreinoService.Ataque.CSRF, pedidoReal());
        assertFalse(r.barrado());
        assertTrue(r.explicacao().contains("Nenhum porteiro barrou"));
    }

    @Test
    void pedidoRealNaoEMexido() {
        // O simular() nem recebe a resposta de verdade: o porteiro só escreve no rascunho.
        MockHttpServletRequest real = pedidoReal();
        servico().simular(TreinoService.Ataque.REBINDING, real);
        assertEquals("POST", real.getMethod(), "o pedido real continua igual");
        assertEquals("127.0.0.1", real.getServerName());
        assertEquals("/central/acoes/treino", real.getRequestURI());
    }

    @Test
    void nomesDosAtaques_listaFechada() {
        assertTrue(TreinoService.Ataque.de("sem-cracha").isPresent());
        assertTrue(TreinoService.Ataque.de("SEM_CRACHA").isEmpty());
        assertTrue(TreinoService.Ataque.de("sqli").isEmpty());
        assertTrue(TreinoService.Ataque.de(null).isEmpty());
    }
}
