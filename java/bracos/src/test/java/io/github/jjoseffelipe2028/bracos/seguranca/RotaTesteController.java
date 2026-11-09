package io.github.jjoseffelipe2028.bracos.seguranca;

import java.util.Map;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
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

    /** PUT, PATCH e DELETE: prova que o porteiro não protege só POST. */
    @RequestMapping(value = "/teste-metodos",
            method = {RequestMethod.PUT, RequestMethod.PATCH, RequestMethod.DELETE})
    Map<String, String> testeMetodos() {
        return Map.of("ok", "sim");
    }

    /** Ticket 008: parâmetro numérico (para testar "abc" → 400). */
    @org.springframework.web.bind.annotation.GetMapping("/teste-numero")
    Map<String, String> numero(@org.springframework.web.bind.annotation.RequestParam int n) {
        return Map.of("n", String.valueOf(n));
    }

    /** Ticket 008: erro inesperado com dado sensível na mensagem. Não pode vazar. */
    @PostMapping("/teste-explode")
    Map<String, String> explode() {
        throw new UnsupportedOperationException("C:/Users/segredo/senha.txt");
    }
}
