package io.github.jjoseffelipe2028.bracos.central;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import io.github.jjoseffelipe2028.bracos.acoes.estudio.BraboRequest;
import io.github.jjoseffelipe2028.bracos.acoes.estudio.BraboService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Vídeo do apresentador pela Central (Ticket 052). 🎙️
 * - GET  /central/acoes/brabo/cortes: cortes da pasta (para o Brabo comentar).
 * - POST /central/acoes/brabo: começa (personagem + tema; Brabo também corte e @).
 * - GET  /central/acoes/brabo: em que etapa está (e o nome do vídeo quando fica pronto).
 * - GET  /central/acoes/brabo/video/{nome}: o vídeo pronto (só …-brabo.mp4 / …-mestre.mp4 da pasta dos cortes).
 * - Os porteiros da Central rodam antes (Host, cabeçalho, Origin no POST e crachá). O log não guarda o tema.
 */
@RestController
@RequestMapping("/central/acoes")
public class CentralBraboController {

    private static final Logger log = LoggerFactory.getLogger(CentralBraboController.class);

    private final BraboService brabo;

    public CentralBraboController(BraboService brabo) {
        this.brabo = brabo;
    }

    @GetMapping("/brabo/cortes")
    public Map<String, List<String>> cortes() {
        return Map.of("cortes", brabo.cortes());
    }

    @PostMapping("/brabo")
    public BraboService.Estado iniciar(@Valid @RequestBody BraboRequest pedido) {
        BraboService.Estado estado = brabo.iniciar(pedido.personagem(), pedido.tema(), pedido.listaDeCortes(), pedido.arroba(),
                pedido.comDupla());
        log.info("cliente=central acao=brabo resultado=iniciado dupla={}", pedido.comDupla());
        return estado;
    }

    @GetMapping("/brabo")
    public BraboService.Estado estado() {
        return brabo.estado();
    }

    @GetMapping("/brabo/video/{nome}")
    public ResponseEntity<Resource> video(@PathVariable String nome) {
        Path arquivo = brabo.video(nome).orElse(null);
        if (arquivo == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("video/mp4"))
                .cacheControl(CacheControl.noStore())
                .header("Content-Disposition", "inline; filename=\"" + arquivo.getFileName() + "\"")
                .body(new FileSystemResource(arquivo));
    }
}
