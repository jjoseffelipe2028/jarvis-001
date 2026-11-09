package io.github.jjoseffelipe2028.bracos.acoes.video;

import java.util.List;

/**
 * Resposta do porteiro: libera ou não a dublagem, e por quê.
 * Exemplo: {"libera":true,"motivos":["proporção 3s→1min acima de 50%"]}
 */
public record Avaliacao(boolean libera, List<String> motivos) {

    public Avaliacao {
        motivos = List.copyOf(motivos); // lista imutável: ninguém altera depois
    }
}
