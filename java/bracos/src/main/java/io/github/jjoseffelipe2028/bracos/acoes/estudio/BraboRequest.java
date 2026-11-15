package io.github.jjoseffelipe2028.bracos.acoes.estudio;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Pedido de vídeo do apresentador pela Central (Ticket 052). 🎙️
 * - personagem: "mestre" (canal de casa) ou "brabo" (canal de GTA).
 * - tema: o que o vídeo ensina/comenta (vai num ARQUIVO para a 001, nunca vira argumento).
 * - corte e arroba: só para o Brabo (corte da pasta dos cortes + @ do dono, com autorização).
 * O {@link BraboService} confere tudo de novo.
 */
public record BraboRequest(
        @NotBlank @Size(max = 10) String personagem,
        @NotBlank @Size(max = 300) String tema,
        @Size(max = 200) String corte,
        @Size(max = 31) String arroba,
        Boolean dupla
) {
    public boolean comDupla() {
        return dupla == null || dupla;
    }
}
