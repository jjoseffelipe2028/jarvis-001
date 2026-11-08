package io.github.jjoseffelipe2028.bracos.seguranca;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.github.jjoseffelipe2028.bracos.acoes.daily.DailyService;
import io.github.jjoseffelipe2028.bracos.erros.ErrosHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Contrato: TODA rota que aceita POST precisa estar atrás do token. 🔒
 * Sobe TODOS os controllers e tenta cada rota POST sem token: tem que dar 401.
 * Se alguém criar uma rota POST fora de /acoes/**, este teste quebra.
 */
@WebMvcTest
@Import({DailyService.class, WebConfig.class, ErrosHandler.class})
@TestPropertySource(properties = "jarvis.token=token-de-teste")
class RotasPostContratoTest {

    private static final Set<RequestMethod> QUE_ALTERAM =
            Set.of(RequestMethod.POST, RequestMethod.PUT, RequestMethod.PATCH, RequestMethod.DELETE);

    private static final String SPRING_ERRO =
            "org.springframework.boot.webmvc.autoconfigure.error.BasicErrorController";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RequestMappingHandlerMapping mapeamentos;

    @Test
    void todaRotaPostExigeToken() throws Exception {
        List<String> rotasPost = new ArrayList<>();
        for (Map.Entry<RequestMappingInfo, HandlerMethod> e : mapeamentos.getHandlerMethods().entrySet()) {
            // Única exceção: a página de erro interna do próprio Spring Boot (/error).
            // Ela não executa ação nenhuma, só mostra o erro. Comparação pelo nome exato da classe.
            if (e.getValue().getBeanType().getName().equals(SPRING_ERRO)) {
                continue;
            }
            Set<RequestMethod> metodos = e.getKey().getMethodsCondition().getMethods();
            // Rota sem método definido aceita QUALQUER método: testa todos os que alteram dados
            Set<RequestMethod> alvo = metodos.isEmpty() ? QUE_ALTERAM : metodos;
            for (RequestMethod m : alvo) {
                if (QUE_ALTERAM.contains(m)) {
                    for (String rota : e.getKey().getPatternValues()) {
                        rotasPost.add(m + " " + rota);
                    }
                }
            }
        }
        assertFalse(rotasPost.isEmpty(), "o teste precisa achar pelo menos uma rota que altera dados");

        List<String> semProtecao = new ArrayList<>();
        for (String metodoERota : rotasPost) {
            String[] partes = metodoERota.split(" ", 2);
            String url = partes[1].replaceAll("\\{[^}]+}", "x"); // troca {variavel} por um valor qualquer
            int status = mockMvc.perform(MockMvcRequestBuilders.request(HttpMethod.valueOf(partes[0]), url)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"texto\":\"oi\"}"))
                    .andReturn().getResponse().getStatus();
            if (status != 401) {
                semProtecao.add(metodoERota + " → " + status);
            }
        }
        assertEquals(List.of(), semProtecao, "rotas que alteram dados sem exigir token");
    }

    /** Uma rota PUT/DELETE nova também precisa de token (achado 3 do jarvis-hacker). */
    @Test
    void putEDeleteTambemExigemToken() throws Exception {
        // Rotas que existem só nos testes (RotaTesteController), uma para cada método
        for (HttpMethod m : List.of(HttpMethod.PUT, HttpMethod.PATCH, HttpMethod.DELETE)) {
            int status = mockMvc.perform(MockMvcRequestBuilders.request(m, "/acoes/teste-metodos"))
                    .andReturn().getResponse().getStatus();
            assertEquals(401, status, m + " sem token");
        }
    }
}
