package io.github.jjoseffelipe2028.bracos.central;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.jjoseffelipe2028.bracos.auditoria.Cliente;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/** Quem vira evento e quem não vira (Ticket 017). 🔒 */
class EventosInterceptorTest {

    private static QuadroDeEventos.Leitura depoisDe(String metodo, String rota, int status, String cliente) {
        QuadroDeEventos quadro = new QuadroDeEventos();
        MockHttpServletRequest pedido = new MockHttpServletRequest(metodo, rota);
        if (cliente != null) {
            pedido.addHeader(Cliente.CABECALHO, cliente);
        }
        MockHttpServletResponse resposta = new MockHttpServletResponse();
        resposta.setStatus(status);
        new EventosInterceptor(quadro).afterCompletion(pedido, resposta, new Object(), null);
        return quadro.depois(0);
    }

    @ParameterizedTest
    @CsvSource({
            "POST, /acoes/daily, daily",
            "GET, /acoes/pendencias, pendencias",
            "GET, /acoes/git-status, git-status",
            "POST, /acoes/video/avaliar, video-avaliar",
            "POST, /acoes/video/relatorio, video-relatorio"})
    void rotaDaLista_viraEvento(String metodo, String rota, String acao) {
        QuadroDeEventos.Leitura l = depoisDe(metodo, rota, 200, "python");
        assertEquals(1, l.eventos().size());
        assertEquals(acao, l.eventos().get(0).acao());
        assertEquals("ok", l.eventos().get(0).resultado());
        assertEquals("python", l.eventos().get(0).cliente());
    }

    @ParameterizedTest
    @CsvSource({
            "GET, /acoes/hora",          // a Central consulta a cada minuto: seria barulho
            "GET, /acoes/prova",
            "GET, /acoes/daily",         // método errado
            "POST, /acoes/pendencias",
            "GET, /acoes/pendencias/",   // variação da rota: comparação exata
            "GET, /acoes/PENDENCIAS",
            "GET, /central/acoes/pendencias"})
    void foraDaLista_naoViraEvento(String metodo, String rota) {
        assertTrue(depoisDe(metodo, rota, 200, "python").eventos().isEmpty());
    }

    @Test
    void clienteInventado_viraDesconhecido() {
        QuadroDeEventos.Leitura l = depoisDe("GET", "/acoes/pendencias", 200, "<script>alert(1)</script>");
        assertEquals(Cliente.DESCONHECIDO, l.eventos().get(0).cliente());
    }

    @ParameterizedTest
    @CsvSource({"200, ok", "201, ok", "400, invalido", "401, recusado", "403, recusado", "503, indisponivel", "500, erro"})
    void statusViraResultadoFixo(int status, String esperado) {
        assertEquals(esperado, depoisDe("GET", "/acoes/pendencias", status, null).eventos().get(0).resultado());
    }

    @Test
    void excecao_viraErro() {
        assertEquals("erro", EventosInterceptor.resultado(200, new IllegalStateException("x")));
    }
}
