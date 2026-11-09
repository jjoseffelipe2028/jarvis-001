package io.github.jjoseffelipe2028.bracos.acoes.video;

import java.util.ArrayList;
import java.util.List;

import io.github.jjoseffelipe2028.bracos.erros.EntradaInvalidaException;
import org.springframework.stereotype.Service;

/**
 * Regra do "Laboratório Brasil" (V1 do módulo de vídeos).
 * O Brasil é o laboratório: só o vídeo que provar retenção aqui ganha dublagem para fora.
 *
 * Libera se bater PELO MENOS UM benchmark:
 * - proporção 3s→1min ACIMA de 50%, ou
 * - média assistida de 74 s (1m14s) ou mais.
 * Bloqueia sempre:
 * - média de 20 s ou menos (alerta de enterro: tráfego ruim ou gancho fraco);
 * - vídeo com menos de 60 s (a métrica 3s→1min não existe nele).
 *
 * Regra pura: não lê arquivo, não chama rede, não executa nada. Fácil de testar.
 */
@Service
public class PortaoDoLaboratorio {

    static final int DURACAO_MINIMA_SEG = 60;
    static final double PROPORCAO_MINIMA = 50.0; // precisa ser MAIOR que isso
    static final int MEDIA_MINIMA_SEG = 74;      // 1m14s
    static final int ALERTA_ENTERRO_SEG = 20;

    public Avaliacao avaliar(int duracaoSeg, double proporcao3sPara1min, int mediaAssistidaSeg) {
        if (mediaAssistidaSeg > duracaoSeg) {
            // Ninguém assiste em média mais do que o vídeo dura: número digitado errado
            throw new EntradaInvalidaException("media maior que a duracao");
        }
        if (duracaoSeg < DURACAO_MINIMA_SEG) {
            return new Avaliacao(false, List.of("vídeo com menos de 1 min: curto demais para esta regra"));
        }
        if (mediaAssistidaSeg <= ALERTA_ENTERRO_SEG) {
            return new Avaliacao(false, List.of("🚨 alerta de enterro: média de 20 s ou menos (tráfego ruim ou gancho fraco)"));
        }

        List<String> motivos = new ArrayList<>();
        if (proporcao3sPara1min > PROPORCAO_MINIMA) {
            motivos.add("proporção 3s→1min acima de 50%");
        }
        if (mediaAssistidaSeg >= MEDIA_MINIMA_SEG) {
            motivos.add("média assistida de 1m14s ou mais");
        }
        if (!motivos.isEmpty()) {
            return new Avaliacao(true, motivos);
        }
        return new Avaliacao(false, List.of(
                "proporção 3s→1min de 50% ou menos",
                "média assistida abaixo de 1m14s"));
    }
}
