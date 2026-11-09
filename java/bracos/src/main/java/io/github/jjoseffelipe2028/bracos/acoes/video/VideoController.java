package io.github.jjoseffelipe2028.bracos.acoes.video;

import io.github.jjoseffelipe2028.bracos.auditoria.Cliente;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * POST /acoes/video/avaliar → o vídeo pode ir para a dublagem? 🔒
 * Fica em /acoes/**: já nasce atrás dos porteiros (Host + assinatura).
 * Exemplo: {"duracaoSeg":190,"proporcao3sPara1min":55.0,"mediaAssistidaSeg":80}
 */
@RestController
@RequestMapping("/acoes/video")
public class VideoController {

    private static final Logger log = LoggerFactory.getLogger(VideoController.class);

    private final PortaoDoLaboratorio portao;
    private final AnalisadorDeRetencao analisador;
    private final RelatorioService relatorios;

    public VideoController(PortaoDoLaboratorio portao, AnalisadorDeRetencao analisador, RelatorioService relatorios) {
        this.portao = portao;
        this.analisador = analisador;
        this.relatorios = relatorios;
    }

    @PostMapping("/avaliar")
    public Avaliacao avaliar(
            @Valid @RequestBody AvaliacaoRequest pedido,
            @RequestHeader(value = Cliente.CABECALHO, required = false) String clienteBruto) {

        String cliente = Cliente.de(clienteBruto);
        try {
            Avaliacao resultado = portao.avaliar(
                    pedido.duracaoSeg(), pedido.proporcao3sPara1min(), pedido.mediaAssistidaSeg());
            // Log só com texto fixo: quem pediu, a ação e a decisão
            log.info("cliente={} acao=video_avaliar resultado={}", cliente, resultado.libera() ? "libera" : "bloqueia");
            return resultado;
        } catch (RuntimeException e) {
            log.warn("cliente={} acao=video_avaliar resultado=erro motivo={}", cliente, e.getClass().getSimpleName());
            throw e;
        }
    }

    /**
     * POST /acoes/video/relatorio → analisa a retenção e grava uma nota em decisions/videos (V2). 🔒
     * Devolve só o NOME da nota, a previsão, a decisão de dublagem e os achados.
     */
    @PostMapping("/relatorio")
    public Analise relatorio(
            @Valid @RequestBody RelatorioRequest pedido,
            @RequestHeader(value = Cliente.CABECALHO, required = false) String clienteBruto) {

        String cliente = Cliente.de(clienteBruto);
        try {
            Analise analise = analisador.analisar(pedido);
            String arquivo = relatorios.gravar(pedido, analise);
            // Nunca loga o título nem o caminho
            log.info("cliente={} acao=video_relatorio resultado=ok", cliente);
            return analise.comArquivo(arquivo);
        } catch (RuntimeException e) {
            log.warn("cliente={} acao=video_relatorio resultado=erro motivo={}", cliente, e.getClass().getSimpleName());
            throw e;
        }
    }
}
