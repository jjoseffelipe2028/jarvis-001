package io.github.jjoseffelipe2028.bracos.central;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import io.github.jjoseffelipe2028.bracos.acoes.pendencias.PendenciasController;
import io.github.jjoseffelipe2028.bracos.acoes.pendencias.PendenciasService;
import io.github.jjoseffelipe2028.bracos.auditoria.Cliente;
import io.github.jjoseffelipe2028.bracos.erros.ErrosHandler;
import io.github.jjoseffelipe2028.bracos.seguranca.TokenInterceptor;
import io.github.jjoseffelipe2028.bracos.seguranca.WebConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** O braço real anota no quadro, inclusive quando o porteiro recusa (Ticket 017). 🔒 */
@WebMvcTest(PendenciasController.class)
@Import({QuadroDeEventos.class, WebConfig.class, ErrosHandler.class})
@TestPropertySource(properties = "jarvis.token=token-de-teste") // valor FICTÍCIO
class EventosDePontaAPontaTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private QuadroDeEventos quadro;

    @MockitoBean
    private PendenciasService pendencias;

    @Test
    void pedidoComTokenViraOk_semTokenViraRecusado() throws Exception {
        when(pendencias.listar()).thenReturn(new PendenciasService.Resultado(List.of(), 0, 0));
        long antes = quadro.depois(0).ultimo();

        mockMvc.perform(get("/acoes/pendencias").header(TokenInterceptor.CABECALHO, "token-de-teste")
                        .header(Cliente.CABECALHO, "python"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/acoes/pendencias")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/acoes/hora")); // fora da lista: não anota, seja qual for a resposta

        List<QuadroDeEventos.Evento> novos = quadro.depois(antes).eventos();
        assertEquals(2, novos.size(), "a hora não vira evento");
        assertEquals("ok", novos.get(0).resultado());
        assertEquals("python", novos.get(0).cliente());
        assertEquals("recusado", novos.get(1).resultado());
        assertEquals(Cliente.DESCONHECIDO, novos.get(1).cliente());
    }
}
