package io.github.jjoseffelipe2028.bracos.seguranca;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.github.jjoseffelipe2028.bracos.acoes.daily.DailyService;
import io.github.jjoseffelipe2028.bracos.acoes.git.GitStatusService;
import io.github.jjoseffelipe2028.bracos.acoes.pendencias.PendenciasService;
import io.github.jjoseffelipe2028.bracos.acoes.video.PortaoDoLaboratorio;
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
@Import({DailyService.class, PendenciasService.class, GitStatusService.class, PortaoDoLaboratorio.class,
        WebConfig.class, ErrosHandler.class})
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

    /**
     * Contrato do Ticket 003: GET também é fechado por padrão. 🔒
     * Toda rota GET em /acoes/** fora de ROTAS_PUBLICAS sem token → 401.
     * Se alguém criar um GET novo com dado pessoal e esquecer o token, este teste quebra.
     */
    @Test
    void todaRotaGetForaDaListaPublicaExigeToken() throws Exception {
        List<String> rotasGet = new ArrayList<>();
        for (Map.Entry<RequestMappingInfo, HandlerMethod> e : mapeamentos.getHandlerMethods().entrySet()) {
            if (e.getValue().getBeanType().getName().equals(SPRING_ERRO)) {
                continue;
            }
            Set<RequestMethod> metodos = e.getKey().getMethodsCondition().getMethods();
            if (!metodos.isEmpty() && !metodos.contains(RequestMethod.GET)) {
                continue;
            }
            for (String rota : e.getKey().getPatternValues()) {
                if (rota.startsWith("/acoes/") && !TokenInterceptor.ROTAS_PUBLICAS.contains(rota)) {
                    rotasGet.add(rota);
                }
            }
        }
        assertFalse(rotasGet.isEmpty(), "o teste precisa achar pelo menos uma rota GET protegida");

        List<String> semProtecao = new ArrayList<>();
        for (String rota : rotasGet) {
            String url = rota.replaceAll("\\{[^}]+}", "x");
            int status = mockMvc.perform(MockMvcRequestBuilders.get(url)).andReturn().getResponse().getStatus();
            if (status != 401) {
                semProtecao.add("GET " + rota + " → " + status);
            }
        }
        assertEquals(List.of(), semProtecao, "rotas GET com dado sem exigir token");
    }

    @Test
    void getEmRotaQueNaoExisteSemToken_401() throws Exception {
        // Fechado por padrão: nem dá para descobrir quais rotas existem sem o token
        int status = mockMvc.perform(MockMvcRequestBuilders.get("/acoes/nao-existe"))
                .andReturn().getResponse().getStatus();
        assertEquals(401, status);
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
