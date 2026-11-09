package io.github.jjoseffelipe2028.bracos.seguranca;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.regex.Pattern;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import io.github.jjoseffelipe2028.bracos.erros.AcaoIndisponivelException;
import io.github.jjoseffelipe2028.bracos.erros.EntradaInvalidaException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * GET /acoes/prova?desafio=... → "aperto de mão" (challenge-response). 🔒
 *
 * Problema: se a API estiver desligada, qualquer programa do PC pode abrir a porta 8080
 * e se passar pelo Jarvis. O Python mandaria o token para ele.
 *
 * Solução: ANTES de mandar o token, o Python manda um número aleatório (desafio).
 * Só quem CONHECE o token consegue devolver HMAC-SHA256(token, "jarvis-servidor:" + desafio).
 * O token nunca viaja nesse passo; um impostor não sabe responder e o Python para.
 *
 * Rota pública (sem token) de propósito: é ela que decide se o token pode ir.
 * O prefixo "jarvis-servidor:" separa esta prova de qualquer outro uso do segredo.
 */
@RestController
@RequestMapping("/acoes")
public class ProvaController {

    static final String PREFIXO = "jarvis-servidor:";

    // Desafio: só hexadecimal minúsculo, 32 a 128 letras (16 a 64 bytes aleatórios)
    private static final Pattern DESAFIO = Pattern.compile("[0-9a-f]{32,128}");

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ProvaController.class);

    private final byte[] chave; // null = token não configurado

    public ProvaController(@Value("${jarvis.token:}") String token) {
        this.chave = (token == null || token.isBlank()) ? null : token.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Ticket 008: rota pública com limite de ritmo. Alto de propósito (o HMAC é barato):
     * corta só abuso pesado, sem deixar um programa qualquer travar o aperto de mão do Python.
     */
    static final int LIMITE_POR_SEGUNDO = 300;
    private long segundoAtual = -1;
    private int noSegundo = 0;

    /** 0 = dentro do ritmo · 1 = primeira recusa do segundo (loga) · 2 = recusa repetida (não loga). */
    private synchronized int ritmo() {
        long agora = System.currentTimeMillis() / 1000;
        if (agora != segundoAtual) {
            segundoAtual = agora;
            noSegundo = 0;
        }
        noSegundo++;
        return noSegundo <= LIMITE_POR_SEGUNDO ? 0 : noSegundo == LIMITE_POR_SEGUNDO + 1 ? 1 : 2;
    }

    @GetMapping("/prova")
    public Map<String, String> prova(@RequestParam(value = "desafio", required = false) String desafio) {
        int r = ritmo();
        if (r != 0) {
            if (r == 1) {
                log.warn("acao=prova resultado=negado motivo=ritmo"); // 1 linha por segundo, sem o desafio
            }
            throw new AcaoIndisponivelException("muitos pedidos");
        }
        if (desafio == null || !DESAFIO.matcher(desafio).matches()) {
            throw new EntradaInvalidaException("desafio invalido");
        }
        if (chave == null) {
            throw new AcaoIndisponivelException("token nao configurado");
        }
        return Map.of("prova", hmac(chave, PREFIXO + desafio));
    }

    static String hmac(byte[] chave, String mensagem) {
        return HexFormat.of().formatHex(hmacBytes(chave, mensagem));
    }

    static byte[] hmacBytes(byte[] chave, String mensagem) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(chave, "HmacSHA256"));
            return mac.doFinal(mensagem.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HMAC indisponivel", e);
        }
    }
}
