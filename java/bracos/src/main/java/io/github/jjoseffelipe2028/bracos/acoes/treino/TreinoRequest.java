package io.github.jjoseffelipe2028.bracos.acoes.treino;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** Pedido do treino (Ticket 064): só o NOME do ataque, de uma lista fechada. Fora dela → 400. 🔒 */
public record TreinoRequest(
        @NotBlank @Pattern(regexp = "csrf|rebinding|sem-cracha|token-falso") String ataque
) {
}
