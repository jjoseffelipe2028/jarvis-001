package io.github.jjoseffelipe2028.bracos.auditoria;

import java.util.regex.Pattern;

/**
 * Limpa texto vindo de fora antes de ir para o log (anti log injection). 🔒
 * Troca caractere de controle (quebra de linha, ANSI etc.) por "_" e corta texto grande.
 */
public final class LogSeguro {

    public static final int LIMITE = 100;
    public static final String VAZIO = "-";
    private static final String SUFIXO = "...";

    private static final Pattern CONTROLE = Pattern.compile("[\\p{Cc}\\u2028\\u2029]");

    private LogSeguro() {
    }

    /** Nulo vira "-"; caractere de controle vira "_"; acima de 100 caracteres corta e põe "...". */
    public static String limpar(String valor) {
        if (valor == null) {
            return VAZIO;
        }
        String limpo = CONTROLE.matcher(valor).replaceAll("_");
        if (limpo.length() > LIMITE) {
            return limpo.substring(0, LIMITE) + SUFIXO;
        }
        return limpo;
    }
}
