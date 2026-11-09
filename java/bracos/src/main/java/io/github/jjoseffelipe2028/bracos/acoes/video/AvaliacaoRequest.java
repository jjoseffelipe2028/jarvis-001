package io.github.jjoseffelipe2028.bracos.acoes.video;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Números de um vídeo da página brasileira (copiados do painel da plataforma). 🔒
 * Fora dos limites → 400 (o @Valid barra antes de chegar na regra).
 *
 * @param duracaoSeg          duração do vídeo, em segundos (1 s a 10 h)
 * @param proporcao3sPara1min de quem viu 3 s, quantos % chegaram a 1 min (0 a 100)
 * @param mediaAssistidaSeg   tempo médio assistido, em segundos
 */
public record AvaliacaoRequest(
        @NotNull @Min(1) @Max(36_000) Integer duracaoSeg,
        @NotNull @DecimalMin("0.0") @DecimalMax("100.0") Double proporcao3sPara1min,
        @NotNull @Min(0) @Max(36_000) Integer mediaAssistidaSeg
) {
}
