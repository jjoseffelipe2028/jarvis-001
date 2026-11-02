package io.github.jjoseffelipe2028.bracos.acoes;

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Testa o contrato do GET /acoes/hora: se o formato mudar, o Python quebra,
 * então este teste avisa antes.
 */
@WebMvcTest(AcoesController.class) // sobe só o controller, sem ligar o servidor
class AcoesControllerTest {

    @Autowired
    private MockMvc mockMvc; // simulador de pedidos HTTP

    @Test
    void horaDevolveHoraEDataNoFormatoCerto() throws Exception {
        mockMvc.perform(get("/acoes/hora"))
                .andExpect(status().isOk())
                // HH:mm → 00:00 até 23:59
                .andExpect(jsonPath("$.hora").value(matchesPattern("([01]\\d|2[0-3]):[0-5]\\d")))
                // dd/MM/yyyy → ex.: 02/11/2026
                .andExpect(jsonPath("$.data").value(matchesPattern("(0[1-9]|[12]\\d|3[01])/(0[1-9]|1[0-2])/\\d{4}")));
    }
}
