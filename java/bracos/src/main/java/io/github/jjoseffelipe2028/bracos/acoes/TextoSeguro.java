package io.github.jjoseffelipe2028.bracos.acoes;

import java.util.regex.Pattern;

/**
 * "Desarma" texto que vem de fora antes de ir para uma nota do Obsidian. 🔒
 * Usado pelo daily (Ticket 002) e pelo relatório de vídeo (Ticket 013): a MESMA proteção nos dois.
 */
public final class TextoSeguro {

    // Controle (Cc), invisíveis (Cf, ex.: U+202E que inverte o texto), surrogate solto (Cs)
    // e separadores de linha Unicode. O U+200D fica: ele monta emojis compostos.
    private static final Pattern INVISIVEL =
            Pattern.compile("[[\\p{Cc}\\p{Cf}\\p{Cs}\\u2028\\u2029]&&[^\\u200D]]");

    // Caracteres que ativam marcação no Obsidian: imagem ![](), link [], código `, HTML <>,
    // DataviewJS $=, Templater <% %>, chaves {} e tabela |. A barra \ primeiro (é o próprio escape).
    private static final Pattern MARCACAO = Pattern.compile("([\\\\`*_\\[\\]<>!$%{}|])");

    private TextoSeguro() {
    }

    /**
     * 1) invisíveis e quebras de linha viram espaço (anti injeção de linhas e "Trojan Source");
     * 2) marcação ganha "\" na frente: o Obsidian mostra o caractere, mas não executa nada.
     */
    public static String desarmar(String texto) {
        if (texto == null) {
            return "";
        }
        String semInvisivel = INVISIVEL.matcher(texto).replaceAll(" ").strip();
        return MARCACAO.matcher(semInvisivel).replaceAll("\\\\$1");
    }
}
