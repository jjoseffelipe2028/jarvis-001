package io.github.jjoseffelipe2028.bracos.contrato;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.github.jjoseffelipe2028.bracos.acoes.AcoesController;
import io.github.jjoseffelipe2028.bracos.acoes.daily.DailyController;
import io.github.jjoseffelipe2028.bracos.acoes.daily.DailyService;
import io.github.jjoseffelipe2028.bracos.seguranca.ProvaController;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.json.JsonMapper;

/**
 * Ticket 081: guardião do contrato Java ↔ Python. 🤝
 * Lê docs/contratos/bracos-python.json (o MESMO arquivo que o tests/test_contrato.py do Python lê) e confere:
 * 1) a rota existe com aquele método HTTP;
 * 2) o corpo do pedido tem EXATAMENTE os campos combinados, com o mesmo @NotBlank e @Size(max), e com @Valid;
 * 3) a resposta tem TODOS os campos que o Python lê (com o tipo certo).
 * Por quê: o Python lê com .get("campo", padrão). Campo renomeado só no Java não dá erro: vira padrão em silêncio
 * (ex.: isca tocada lida como 0 toques). Aqui isso quebra o build.
 */
class ContratoPythonTest {

    static final Path CONTRATO = Path.of("..", "..", "docs", "contratos", "bracos-python.json");
    private static final String PACOTE = "io.github.jjoseffelipe2028.bracos";
    /** Rotas que devolvem Map (sem record para olhar por reflexão): conferidas com uma chamada de verdade. */
    private static final Set<String> ROTAS_MAPA = Set.of("/acoes/hora", "/acoes/daily", "/acoes/prova");

    @SuppressWarnings("unchecked")
    static List<Map<String, Object>> rotas() throws Exception {
        assertTrue(Files.isRegularFile(CONTRATO), "contrato não encontrado: " + CONTRATO.toAbsolutePath());
        Map<String, Object> raiz = JsonMapper.builder().build().readValue(CONTRATO.toFile(), LinkedHashMap.class);
        return (List<Map<String, Object>>) raiz.get("rotas");
    }

    @TestFactory
    List<DynamicTest> cadaRotaDoContratoBateComOJava() throws Exception {
        Map<String, Method> mapa = rotasDoJava();
        List<DynamicTest> testes = new ArrayList<>();
        for (Map<String, Object> r : rotas()) {
            String chave = r.get("metodo") + " " + r.get("rota");
            testes.add(DynamicTest.dynamicTest(chave, () -> conferir(r, mapa.get(chave), chave)));
        }
        assertFalse(testes.isEmpty());
        return testes;
    }

    @SuppressWarnings("unchecked")
    private static void conferir(Map<String, Object> r, Method metodo, String chave) throws Exception {
        assertNotNull(metodo, chave + ": o Python chama esta rota, mas ela não existe no Java");
        conferirPedido(metodo, (Map<String, Object>) r.get("pedido"), chave);
        Map<String, Object> resposta = (Map<String, Object>) r.get("resposta");
        if (ROTAS_MAPA.contains((String) r.get("rota"))) {
            return; // ver respostaEmMapa_temOsCamposDoContrato
        }
        conferirResposta(metodo.getGenericReturnType(), resposta, chave);
    }

    // ---------- pedido ----------

    @SuppressWarnings("unchecked")
    private static void conferirPedido(Method metodo, Map<String, Object> pedido, String chave) throws Exception {
        Parameter corpo = null;
        for (Parameter p : metodo.getParameters()) {
            if (p.isAnnotationPresent(RequestBody.class)) {
                corpo = p;
            }
        }
        if (pedido.isEmpty()) {
            assertEquals(null, corpo, chave + ": o contrato não manda corpo, mas o Java espera um");
            return;
        }
        assertNotNull(corpo, chave + ": o Python manda corpo, mas o Java não lê nenhum");
        assertTrue(corpo.isAnnotationPresent(Valid.class), chave + ": corpo sem @Valid (entrada sem validação)");
        Class<?> tipo = corpo.getType();
        assertTrue(tipo.isRecord(), chave + ": o corpo deveria ser um record");
        List<String> campos = new ArrayList<>();
        for (RecordComponent c : tipo.getRecordComponents()) {
            campos.add(c.getName());
        }
        assertEquals(Set.copyOf(pedido.keySet()), Set.copyOf(campos), chave + ": campos do pedido diferentes do contrato");
        for (Map.Entry<String, Object> e : pedido.entrySet()) {
            Map<String, Object> regra = (Map<String, Object>) e.getValue();
            var campo = tipo.getDeclaredField(e.getKey());
            boolean obrigatorio = Boolean.TRUE.equals(regra.get("obrigatorio"));
            assertEquals(obrigatorio, campo.isAnnotationPresent(NotBlank.class),
                    chave + " campo " + e.getKey() + ": @NotBlank diferente do 'obrigatorio' do contrato");
            Size size = campo.getAnnotation(Size.class);
            assertNotNull(size, chave + " campo " + e.getKey() + ": sem @Size (texto sem limite)");
            assertEquals(((Number) regra.get("max")).intValue(), size.max(),
                    chave + " campo " + e.getKey() + ": tamanho máximo diferente do contrato");
        }
    }

    // ---------- resposta ----------

    @SuppressWarnings("unchecked")
    private static void conferirResposta(Type tipo, Map<String, Object> exemplo, String onde) {
        Class<?> classe = cru(tipo);
        assertTrue(classe.isRecord(), onde + ": a resposta deveria ser um record (ou entrar em ROTAS_MAPA)");
        Map<String, RecordComponent> componentes = new LinkedHashMap<>();
        for (RecordComponent c : classe.getRecordComponents()) {
            componentes.put(c.getName(), c);
        }
        for (Map.Entry<String, Object> e : exemplo.entrySet()) {
            String lugar = onde + " → " + e.getKey();
            RecordComponent c = componentes.get(e.getKey());
            if (c == null) {
                fail(lugar + ": o Python lê este campo, mas o Java não manda (renomeado ou apagado?)");
            }
            Object valor = e.getValue();
            if (valor instanceof List<?> lista) {
                assertTrue(List.class.isAssignableFrom(c.getType()), lugar + ": deveria ser lista");
                Type item = ((ParameterizedType) c.getGenericType()).getActualTypeArguments()[0];
                if (!lista.isEmpty() && lista.get(0) instanceof Map<?, ?> primeiro) {
                    conferirResposta(item, (Map<String, Object>) primeiro, lugar + "[]");
                } else {
                    assertEquals(String.class, item, lugar + ": deveria ser lista de textos");
                }
            } else {
                conferirTipo(valor, c.getType(), lugar);
            }
        }
    }

    private static void conferirTipo(Object valor, Class<?> tipo, String lugar) {
        if (valor instanceof Boolean) {
            assertTrue(tipo == boolean.class || tipo == Boolean.class, lugar + ": deveria ser boolean");
        } else if (valor instanceof Number) {
            assertTrue(tipo == int.class || tipo == long.class || Number.class.isAssignableFrom(tipo),
                    lugar + ": deveria ser número");
        } else {
            assertEquals(String.class, tipo, lugar + ": deveria ser texto");
        }
    }

    private static Class<?> cru(Type tipo) {
        return tipo instanceof ParameterizedType p ? (Class<?>) p.getRawType() : (Class<?>) tipo;
    }

    // ---------- rotas que devolvem Map: chamada de verdade (sem subir a API) ----------

    @Test
    @SuppressWarnings("unchecked")
    void respostaEmMapa_temOsCamposDoContrato() throws Exception {
        DailyService daily = mock(DailyService.class);
        when(daily.registrar(anyString())).thenReturn("2026-11-15.md");
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new AcoesController(), new DailyController(daily),
                new ProvaController("token-de-teste")).build(); // token FICTÍCIO
        Map<String, String> chamadas = Map.of(
                "/acoes/hora", "GET",
                "/acoes/prova", "GET",
                "/acoes/daily", "POST");
        JsonMapper json = JsonMapper.builder().build();
        int conferidas = 0;
        for (Map<String, Object> r : rotas()) {
            String rota = (String) r.get("rota");
            if (!ROTAS_MAPA.contains(rota)) {
                continue;
            }
            var pedido = "POST".equals(chamadas.get(rota))
                    ? post(rota).contentType(MediaType.APPLICATION_JSON).content("{\"texto\":\"oi\"}")
                    : get(rota + ("/acoes/prova".equals(rota) ? "?desafio=" + "ab".repeat(16) : ""));
            String corpo = mvc.perform(pedido).andReturn().getResponse().getContentAsString();
            Map<String, Object> recebido = json.readValue(corpo, LinkedHashMap.class);
            for (String campo : ((Map<String, Object>) r.get("resposta")).keySet()) {
                assertTrue(recebido.containsKey(campo), rota + " → " + campo + ": o Python lê, o Java não manda");
            }
            conferidas++;
        }
        assertEquals(ROTAS_MAPA.size(), conferidas, "toda rota de ROTAS_MAPA precisa estar no contrato");
    }

    // ---------- mapa "METODO /rota" → método do controller ----------

    static Map<String, Method> rotasDoJava() throws Exception {
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        Map<String, Method> mapa = new LinkedHashMap<>();
        for (BeanDefinition bd : scanner.findCandidateComponents(PACOTE)) {
            Class<?> c = Class.forName(bd.getBeanClassName());
            RequestMapping base = c.getAnnotation(RequestMapping.class);
            String prefixo = base == null ? "" : primeiro(base.value(), base.path());
            for (Method m : c.getDeclaredMethods()) {
                GetMapping g = m.getAnnotation(GetMapping.class);
                PostMapping p = m.getAnnotation(PostMapping.class);
                if (g != null) {
                    mapa.put("GET " + prefixo + primeiro(g.value(), g.path()), m);
                }
                if (p != null) {
                    mapa.put("POST " + prefixo + primeiro(p.value(), p.path()), m);
                }
            }
        }
        return mapa;
    }

    private static String primeiro(String[] valor, String[] caminho) {
        if (valor.length > 0) {
            return valor[0];
        }
        return caminho.length > 0 ? caminho[0] : "";
    }
}
