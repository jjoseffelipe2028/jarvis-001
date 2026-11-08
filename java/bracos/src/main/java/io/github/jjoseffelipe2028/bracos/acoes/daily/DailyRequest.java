package io.github.jjoseffelipe2028.bracos.acoes.daily;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Pedido do braço daily: só o texto da linha. 🔒
 * Vazio ou acima de 2000 caracteres → 400 (o @Valid barra antes de chegar no Service).
 * Repare: NÃO existe campo de arquivo ou pasta. Quem escolhe onde gravar é o servidor.
 */
public record DailyRequest(
        @NotBlank @Size(max = 2000) String texto
) {
}
