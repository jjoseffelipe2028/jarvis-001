package io.github.jjoseffelipe2028.bracos.acoes.video;

import java.util.List;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Números de um vídeo postado, copiados do painel da plataforma (V2). 🔒
 * Repare: NÃO existe campo de arquivo ou pasta. Quem escolhe onde gravar é o servidor.
 *
 * @param titulo     título do vídeo (vai "desarmado" para a nota)
 * @param plataforma só da lista fechada
 * @param ctrPct     de quem viu o vídeo no feed, quantos % clicaram
 * @param quedasSeg  segundos em que o gráfico de retenção despenca (opcional, até 20)
 */
public record RelatorioRequest(
        @NotBlank @Size(max = 120) String titulo,
        @NotNull @Pattern(regexp = "facebook|youtube|tiktok|instagram|kwai") String plataforma,
        @NotNull @Min(1) @Max(36_000) Integer duracaoSeg,
        @NotNull @DecimalMin("0.0") @DecimalMax("100.0") Double proporcao3sPara1min,
        @NotNull @Min(0) @Max(36_000) Integer mediaAssistidaSeg,
        @NotNull @DecimalMin("0.0") @DecimalMax("100.0") Double ctrPct,
        @Size(max = 20) List<@NotNull @Min(0) @Max(36_000) Integer> quedasSeg
) {

    public RelatorioRequest {
        quedasSeg = quedasSeg == null ? List.of() : quedasSeg;
    }
}
