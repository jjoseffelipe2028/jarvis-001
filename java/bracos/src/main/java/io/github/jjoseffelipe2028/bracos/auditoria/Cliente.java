package io.github.jjoseffelipe2028.bracos.auditoria;

import java.util.Set;

/**
 * Quem está chamando a API (cabeçalho X-Jarvis-Cliente).
 * Serve SÓ para o log de auditoria, nunca para decidir segurança. 🔒
 */
public final class Cliente {

    public static final String CABECALHO = "X-Jarvis-Cliente";
    public static final String DESCONHECIDO = "desconhecido";

    private static final Set<String> PERMITIDOS = Set.of("python", "central");

    private Cliente() {
    }

    /** Devolve python/central; qualquer outra coisa (inclusive nulo) vira "desconhecido". */
    public static String de(String valor) {
        if (valor != null && PERMITIDOS.contains(valor)) {
            return valor;
        }
        return DESCONHECIDO;
    }
}
