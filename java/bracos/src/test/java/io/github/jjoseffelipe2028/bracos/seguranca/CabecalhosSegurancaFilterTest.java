package io.github.jjoseffelipe2028.bracos.seguranca;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/** Cabeçalhos de segurança em toda resposta (Ticket 019, achado 1 da revisão de 10/11). 🔒 */
class CabecalhosSegurancaFilterTest {

    private static MockHttpServletResponse passar(String rota) throws Exception {
        MockHttpServletRequest pedido = new MockHttpServletRequest("GET", rota);
        MockHttpServletResponse resposta = new MockHttpServletResponse();
        MockFilterChain cadeia = new MockFilterChain();
        new CabecalhosSegurancaFilter().doFilter(pedido, resposta, cadeia);
        assertTrue(cadeia.getRequest() != null, "o filtro não pode parar o pedido");
        return resposta;
    }

    @ParameterizedTest
    @ValueSource(strings = {"/", "/index.html", "/central.js", "/acoes/hora", "/acoes/pendencias", "/central/sessao"})
    void todaRespostaGanhaAsTravas(String rota) throws Exception {
        MockHttpServletResponse r = passar(rota);
        assertEquals("DENY", r.getHeader("X-Frame-Options"), "contra iframe (clickjacking)");
        assertEquals("frame-ancestors 'none'", r.getHeader("Content-Security-Policy"));
        assertEquals("nosniff", r.getHeader("X-Content-Type-Options"));
        assertEquals("no-referrer", r.getHeader("Referrer-Policy"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/acoes/pendencias", "/acoes/daily", "/central/acoes/pendencias", "/central/acoes/eventos"})
    void rotaComDado_naoFicaEmCache(String rota) throws Exception {
        assertEquals("no-store", passar(rota).getHeader("Cache-Control"));
    }

    @Test
    void paginaEstatica_semNoStore() throws Exception {
        assertNull(passar("/index.html").getHeader("Cache-Control"));
        assertFalse(CabecalhosSegurancaFilter.temDado("/central.js"));
        assertFalse(CabecalhosSegurancaFilter.temDado(null));
    }
}
