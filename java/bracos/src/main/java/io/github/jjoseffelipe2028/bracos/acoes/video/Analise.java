package io.github.jjoseffelipe2028.bracos.acoes.video;

import java.util.List;

/**
 * Resultado da análise de retenção (V2).
 * Exemplo: {"arquivo":"2026-11-09-164210-retencao.md","previsao":"forte","dublagem":{...},"achados":[]}
 *
 * @param arquivo  só o NOME da nota gravada (nunca o caminho); vazio antes de gravar
 * @param previsao forte · promissor, com ajustes · abaixo da meta · fraco · sem previsão
 * @param dublagem decisão do porteiro do V1
 * @param achados  erros identificados + ajustes necessários
 */
public record Analise(String arquivo, String previsao, Avaliacao dublagem, List<Achado> achados) {

    public Analise {
        achados = List.copyOf(achados);
    }

    Analise comArquivo(String nome) {
        return new Analise(nome, previsao, dublagem, achados);
    }
}
