package io.github.jjoseffelipe2028.bracos.acoes.estudio;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Pedido de vídeo do apresentador pela Central (Ticket 052). 🎙️
 * - personagem: "mestre" (canal de casa) ou "brabo" (canal de GTA).
 * - tema: o que o vídeo ensina/comenta (vai num ARQUIVO para a 001, nunca vira argumento).
 * - corte e arroba: só para o Brabo (corte da pasta dos cortes + @ do dono, com autorização).
 * - cortes: Ticket 053, vários cortes para o Brabo comentar num pedido só (até 8).
 * O {@link BraboService} confere tudo de novo.
 */
public record BraboRequest(
        @NotBlank @Size(max = 10) String personagem,
        @NotBlank @Size(max = 300) String tema,
        @Size(max = 200) String corte,
        @Size(max = 31) String arroba,
        Boolean dupla,
        @Size(max = 8) List<@Size(max = 200) String> cortes
) {
    /** Os cortes do pedido: a lista (053) ou o corte único (052). */
    public List<String> listaDeCortes() {
        if (cortes != null && !cortes.isEmpty()) {
            return cortes;
        }
        return corte == null || corte.isBlank() ? List.of() : List.of(corte);
    }

    public boolean comDupla() {
        return dupla == null || dupla;
    }
}
