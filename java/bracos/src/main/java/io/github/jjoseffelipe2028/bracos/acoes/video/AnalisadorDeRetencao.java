package io.github.jjoseffelipe2028.bracos.acoes.video;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

import io.github.jjoseffelipe2028.bracos.erros.EntradaInvalidaException;
import org.springframework.stereotype.Service;

/**
 * Análise de retenção (V2 do módulo de vídeos): números do painel → erros + ajustes + previsão.
 * Benchmarks do curso (Módulos 07, 08 e 09):
 * - Facebook paga 5x a 10x mais em vídeo de 3 min ou mais (meta: 3:05 depois da edição);
 * - 3s→1min acima de 50%; média de 1m14s em vídeo de ~3 min;
 * - média de 20 s ou menos = alerta de enterro;
 * - o anúncio entra em 1:00: queda logo antes (0:50–0:59) custa dinheiro.
 *
 * Regra pura: não lê arquivo, não chama rede. Quem grava a nota é o RelatorioService.
 */
@Service
public class AnalisadorDeRetencao {

    static final int MINIMO_FACEBOOK_SEG = 180;
    static final int GANCHO_SEG = 3;
    static final int ANUNCIO_SEG = 60;
    static final int ANTES_DO_ANUNCIO_SEG = 50;

    private final PortaoDoLaboratorio portao;

    public AnalisadorDeRetencao(PortaoDoLaboratorio portao) {
        this.portao = portao;
    }

    public Analise analisar(RelatorioRequest r) {
        int duracao = r.duracaoSeg();
        int media = r.mediaAssistidaSeg();
        for (int queda : r.quedasSeg()) {
            if (queda > duracao) {
                throw new EntradaInvalidaException("queda depois do fim do video");
            }
        }

        // Reaproveita o V1: a MESMA regra decide a dublagem (e já recusa média > duração)
        Avaliacao dublagem = portao.avaliar(duracao, r.proporcao3sPara1min(), media);

        boolean longo = duracao >= PortaoDoLaboratorio.DURACAO_MINIMA_SEG;
        boolean enterro = longo && media <= PortaoDoLaboratorio.ALERTA_ENTERRO_SEG;
        List<Achado> achados = new ArrayList<>();

        if (enterro) {
            achados.add(new Achado("🚨 alerta de enterro: média de 20 s ou menos",
                    "conferir de onde veio o tráfego (página de outro nicho?) e refazer o gancho dos 3 primeiros segundos"));
        }
        if ("facebook".equals(r.plataforma()) && duracao < MINIMO_FACEBOOK_SEG) {
            achados.add(new Achado("vídeo abaixo de 3 min no Facebook: o anúncio paga 5x a 10x menos",
                    "roteiro bruto de 4 a 5 min para fechar com pelo menos 3:05 depois da edição"));
        }
        if (longo && r.proporcao3sPara1min() <= PortaoDoLaboratorio.PROPORCAO_MINIMA) {
            achados.add(new Achado("metade ou mais do público sai antes de 1:00",
                    "quebra de monotonia a cada 15 s e pico de curiosidade aos 0:55"));
        }
        if (duracao >= MINIMO_FACEBOOK_SEG && !enterro && media < PortaoDoLaboratorio.MEDIA_MINIMA_SEG) {
            achados.add(new Achado("média assistida abaixo de 1m14s",
                    "cortar os pontos de fuga: trechos parados e explicações longas"));
        }
        for (int queda : new TreeSet<>(r.quedasSeg())) { // ordena e tira repetidos
            achados.add(achadoDaQueda(queda));
        }

        return new Analise("", previsao(longo, enterro, dublagem.libera(), achados.isEmpty()), dublagem, achados);
    }

    private static Achado achadoDaQueda(int seg) {
        String quando = mmss(seg);
        if (seg < GANCHO_SEG) {
            return new Achado("queda no gancho aos " + quando,
                    "trocar a abertura: mostrar o \"fator uau\" já no primeiro segundo");
        }
        if (seg >= ANTES_DO_ANUNCIO_SEG && seg < ANUNCIO_SEG) {
            return new Achado("queda aos " + quando + ", logo antes do anúncio de 1:00",
                    "colocar uma promessa ou pergunta aos 0:55");
        }
        return new Achado("queda aos " + quando, "conferir esse trecho na transcrição e cortar ou acelerar");
    }

    private static String previsao(boolean longo, boolean enterro, boolean libera, boolean semAchados) {
        if (!longo) {
            return "sem previsão (vídeo com menos de 1 min)";
        }
        if (enterro) {
            return "fraco";
        }
        if (libera) {
            return semAchados ? "forte" : "promissor, com ajustes";
        }
        return "abaixo da meta";
    }

    /** 75 → "1:15". */
    static String mmss(int seg) {
        return (seg / 60) + ":" + String.format("%02d", seg % 60);
    }
}
