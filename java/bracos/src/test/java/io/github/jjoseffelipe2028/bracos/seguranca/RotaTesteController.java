package io.github.jjoseffelipe2028.bracos.seguranca;

import java.util.Map;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Rota que existe SÓ nos testes (não vai para produção). */
@RestController
@RequestMapping("/acoes")
class RotaTesteController {

    record PedidoTeste(@NotBlank @Size(max = 10) String texto) {
    }

    @PostMapping("/teste-token")
    Map<String, String> teste(@Valid @RequestBody PedidoTeste pedido) {
        return Map.of("ok", "sim");
    }
}
