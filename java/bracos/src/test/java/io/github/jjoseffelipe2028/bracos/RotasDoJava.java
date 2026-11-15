package io.github.jjoseffelipe2028.bracos;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Ajuda de TESTE (Tickets 081 e 082): lista todas as rotas da API por reflexão, sem subir o servidor.
 * Chave "METODO /rota" → método do controller. Um @RequestMapping num MÉTODO (aceita qualquer verbo) vira "ANY /rota".
 */
public final class RotasDoJava {

    static final String PACOTE = "io.github.jjoseffelipe2028.bracos";

    private RotasDoJava() {
    }

    public static Map<String, Method> mapa() throws ClassNotFoundException {
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        scanner.addIncludeFilter(new AnnotationTypeFilter(org.springframework.stereotype.Controller.class));
        Map<String, Method> mapa = new LinkedHashMap<>();
        for (BeanDefinition bd : scanner.findCandidateComponents(PACOTE)) {
            Class<?> c = Class.forName(bd.getBeanClassName());
            if (deTeste(c)) {
                continue; // controllers falsos dos testes (ex.: RotaTesteController) não são rotas da API
            }
            RequestMapping base = c.getAnnotation(RequestMapping.class);
            String prefixo = base == null ? "" : primeiro(base.value(), base.path());
            for (Method m : c.getDeclaredMethods()) {
                anotar(mapa, "GET", prefixo, m, m.getAnnotation(GetMapping.class) == null ? null
                        : primeiro(m.getAnnotation(GetMapping.class).value(), m.getAnnotation(GetMapping.class).path()));
                anotar(mapa, "POST", prefixo, m, m.getAnnotation(PostMapping.class) == null ? null
                        : primeiro(m.getAnnotation(PostMapping.class).value(), m.getAnnotation(PostMapping.class).path()));
                anotar(mapa, "PUT", prefixo, m, m.getAnnotation(PutMapping.class) == null ? null
                        : primeiro(m.getAnnotation(PutMapping.class).value(), m.getAnnotation(PutMapping.class).path()));
                anotar(mapa, "DELETE", prefixo, m, m.getAnnotation(DeleteMapping.class) == null ? null
                        : primeiro(m.getAnnotation(DeleteMapping.class).value(), m.getAnnotation(DeleteMapping.class).path()));
                anotar(mapa, "PATCH", prefixo, m, m.getAnnotation(PatchMapping.class) == null ? null
                        : primeiro(m.getAnnotation(PatchMapping.class).value(), m.getAnnotation(PatchMapping.class).path()));
                RequestMapping rm = m.getAnnotation(RequestMapping.class);
                if (rm != null) {
                    String verbo = rm.method().length == 1 ? rm.method()[0].name() : "ANY";
                    anotar(mapa, verbo, prefixo, m, primeiro(rm.value(), rm.path()));
                }
            }
        }
        return mapa;
    }

    private static boolean deTeste(Class<?> c) {
        var fonte = c.getProtectionDomain().getCodeSource();
        return fonte != null && fonte.getLocation().toString().contains("test-classes");
    }

    private static void anotar(Map<String, Method> mapa, String verbo, String prefixo, Method m, String caminho) {
        if (caminho != null) {
            mapa.put(verbo + " " + prefixo + caminho, m);
        }
    }

    private static String primeiro(String[] valor, String[] caminho) {
        if (valor.length > 0) {
            return valor[0];
        }
        return caminho.length > 0 ? caminho[0] : "";
    }
}
