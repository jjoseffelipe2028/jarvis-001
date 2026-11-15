package io.github.jjoseffelipe2028.bracos.acoes.pedidos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Pedido para a caixa da 001 (Ticket 060): só o texto. 🔒
 * Não existe campo de arquivo ou pasta: quem escolhe onde gravar é o servidor.
 */
public record PedidoRequest(
        @NotBlank @Size(max = 2000) String texto
) {
}
