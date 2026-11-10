package io.github.jjoseffelipe2028.bracos.acoes.sentinela;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Pedido do detector de golpe (Ticket 023): só o texto da mensagem. 🔒
 * Vazio ou acima de 4000 caracteres → 400. Não existe campo de URL para "abrir": o Jarvis nunca abre link.
 */
public record GolpeRequest(
        @NotBlank @Size(max = 4000) String texto
) {
}
