package io.github.jjoseffelipe2028.bracos.acoes.conversa;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Pedido da conversa (Ticket 039): a fala nova e, se quiser, as últimas falas (para a IA lembrar do assunto). 🔒
 * Limites: fala de até 1000 letras, no máximo 8 falas de histórico. Papel só "user" ou "assistant":
 * não dá para mandar uma fala de "system" e trocar as regras da IA. Fora disso → 400.
 */
public record ChatRequest(
        @NotBlank @Size(max = 1000) String texto,
        @Size(max = 8) List<@Valid Fala> historico
) {
    public record Fala(
            @NotBlank @Pattern(regexp = "user|assistant") String papel,
            @NotBlank @Size(max = 2000) String texto
    ) {
    }
}
