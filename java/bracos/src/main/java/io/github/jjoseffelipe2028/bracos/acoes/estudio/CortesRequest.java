package io.github.jjoseffelipe2028.bracos.acoes.estudio;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Pedido de cortes pela Central (Ticket 048): um vídeo da lista OU um link do YouTube (com autorização),
 * o @ do dono, quantos cortes (1 a 20), idioma da legenda extra (en/es, opcional) e se a dupla revisa. 🔒
 * O {@link CortesService} confere tudo de novo (nome na lista, link limpo, @).
 */
public record CortesRequest(
        @Size(max = 200) String video,
        @Size(max = 300) String link,
        Boolean autorizado,
        @NotBlank @Size(max = 31) String arroba,
        @Min(1) @Max(20) Integer top,
        @Size(max = 2) String idioma,
        Boolean dupla
) {
    public int quantos() {
        return top == null ? 8 : top;
    }

    public boolean comDupla() {
        return dupla == null || dupla;
    }

    public boolean comAutorizacao() {
        return Boolean.TRUE.equals(autorizado);
    }
}
