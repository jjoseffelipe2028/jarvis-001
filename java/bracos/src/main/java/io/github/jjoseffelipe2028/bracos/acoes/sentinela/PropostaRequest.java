package io.github.jjoseffelipe2028.bracos.acoes.sentinela;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Pedido do verificador de propostas (Ticket 030): o texto do e-mail, quem mandou e a marca que diz ser. 🔒
 * Só o texto é obrigatório. Tamanhos limitados → 400 (4000 letras, o mesmo limite testado do detector de golpe).
 * Não existe campo para "abrir" nada.
 */
public record PropostaRequest(
        @NotBlank @Size(max = 4000) String texto,
        @Size(max = 254) String remetente,
        @Size(max = 60) String marca
) {
}
