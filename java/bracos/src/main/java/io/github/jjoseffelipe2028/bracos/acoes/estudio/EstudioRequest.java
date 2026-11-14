package io.github.jjoseffelipe2028.bracos.acoes.estudio;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Pedido ao Estúdio 001 (Ticket 047): o que o José quer (até 2000 letras) e se a 002 revisa o plano.
 * O texto vai para um arquivo (PEDIDO.md), nunca para a linha de comando. 🔒
 */
public record EstudioRequest(@NotBlank @Size(max = 2000) String texto, Boolean revisao) {

    /** Sem dizer nada, a 002 revisa (o ciclo completo é o padrão). */
    public boolean comRevisao() {
        return revisao == null || revisao;
    }
}
