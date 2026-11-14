package io.github.jjoseffelipe2028.bracos.central;

import java.util.List;
import java.util.Map;

import io.github.jjoseffelipe2028.bracos.acoes.estudio.CortesRequest;
import io.github.jjoseffelipe2028.bracos.acoes.estudio.CortesService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Estúdio de cortes pela Central (Ticket 048). 🎬
 * - GET  /central/acoes/cortes/videos: os vídeos da pasta de entrada (só nome e tamanho).
 * - POST /central/acoes/cortes: começa (vídeo da lista ou link com autorização).
 * - GET  /central/acoes/cortes: em que etapa está.
 * - Os porteiros da Central rodam antes (Host, cabeçalho, Origin obrigatório no POST e crachá).
 * - O log não guarda nome de vídeo nem link: só se foi por link e se a dupla revisa.
 */
@RestController
@RequestMapping("/central/acoes")
public class CentralCortesController {

    private static final Logger log = LoggerFactory.getLogger(CentralCortesController.class);

    private final CortesService cortes;

    public CentralCortesController(CortesService cortes) {
        this.cortes = cortes;
    }

    @GetMapping("/cortes/videos")
    public Map<String, List<CortesService.Video>> videos() {
        return Map.of("videos", cortes.videos());
    }

    @PostMapping("/cortes")
    public CortesService.Estado iniciar(@Valid @RequestBody CortesRequest pedido) {
        boolean porLink = pedido.link() != null && !pedido.link().isBlank();
        boolean dupla = pedido.comDupla();
        CortesService.Estado estado = cortes.iniciar(pedido.video(), pedido.link(), pedido.comAutorizacao(), pedido.arroba(),
                pedido.quantos(), pedido.idioma(), dupla);
        log.info("cliente=central acao=cortes resultado=iniciado link={} dupla={}", porLink, dupla);
        return estado;
    }

    @GetMapping("/cortes")
    public CortesService.Estado estado() {
        return cortes.estado();
    }
}
