package io.github.jjoseffelipe2028.bracos.seguranca;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Confere a ASSINATURA de um pedido (Ticket 007). 🔒
 *
 * O cliente nunca manda o token. Ele manda:
 *   X-Jarvis-Hora       segundos desde 1970 (agora)
 *   X-Jarvis-Nonce      número aleatório, usado UMA vez só
 *   X-Jarvis-Assinatura HMAC-SHA256(chave, MÉTODO \n ROTA \n HORA \n NONCE \n SHA256(corpo))
 * onde chave = HMAC-SHA256(token, "jarvis-assinatura-v1") (subchave: o token não vira chave de dois usos).
 *
 * Por que é melhor que mandar o token:
 * - Um impostor que pegar um pedido NÃO consegue fazer outro: não sabe a chave.
 * - Reusar o mesmo pedido (replay) não funciona: o nonce só vale uma vez e a hora vence em 60 s.
 * - Trocar a rota, o método ou o corpo quebra a assinatura.
 */
public class Assinatura {

    public static final String CAB_HORA = "X-Jarvis-Hora";
    public static final String CAB_NONCE = "X-Jarvis-Nonce";
    public static final String CAB_ASSINATURA = "X-Jarvis-Assinatura";

    static final String ROTULO_SUBCHAVE = "jarvis-assinatura-v1";
    static final long JANELA_SEGUNDOS = 60;
    static final int LIMITE_NONCES = 10_000;

    private static final Pattern HORA = Pattern.compile("[0-9]{1,12}");
    private static final Pattern NONCE = Pattern.compile("[0-9a-f]{32,64}");
    private static final Pattern HEX64 = Pattern.compile("[0-9a-f]{64}");

    /** Resultado da conferência. O motivo vai para o log (texto fixo, nunca dado de fora). */
    public enum Resultado { OK, FORMATO, HORA_VENCIDA, REPETIDO, ASSINATURA_ERRADA, LOTADO }

    private final byte[] subchave;
    private final Clock relogio;
    // A lista de nonces some quando a API reinicia: pedido assinado ANTES do início é recusado
    // (senão um pedido copiado poderia ser reenviado logo depois de um reinício)
    private final long inicio;
    // nonce → até quando ele fica guardado (segundos). Depois da janela, já não passaria pela hora.
    private final Map<String, Long> usados = new ConcurrentHashMap<>();

    public Assinatura(String token, Clock relogio) {
        this.subchave = ProvaController.hmacBytes(token.getBytes(StandardCharsets.UTF_8), ROTULO_SUBCHAVE);
        this.relogio = relogio;
        this.inicio = relogio.millis() / 1000;
    }

    public Resultado conferir(String metodo, String rota, String hora, String nonce, byte[] corpo, String assinatura) {
        if (hora == null || nonce == null || assinatura == null
                || !HORA.matcher(hora).matches() || !NONCE.matcher(nonce).matches()
                || !HEX64.matcher(assinatura).matches()) {
            return Resultado.FORMATO;
        }
        long agora = relogio.millis() / 1000;
        long quando = Long.parseLong(hora);
        if (Math.abs(agora - quando) > JANELA_SEGUNDOS || quando < inicio) {
            return Resultado.HORA_VENCIDA;
        }
        String esperado = assinar(subchave, metodo, rota, hora, nonce, corpo);
        // Tempo constante: não dá pista pelo tempo de resposta
        if (!MessageDigest.isEqual(esperado.getBytes(StandardCharsets.US_ASCII),
                assinatura.getBytes(StandardCharsets.US_ASCII))) {
            return Resultado.ASSINATURA_ERRADA;
        }
        // Só guarda o nonce DEPOIS da assinatura certa: quem não tem a chave não consegue lotar a lista
        limparVencidos(agora);
        if (usados.size() >= LIMITE_NONCES) {
            return Resultado.LOTADO;
        }
        if (usados.putIfAbsent(nonce, agora + 2 * JANELA_SEGUNDOS) != null) {
            return Resultado.REPETIDO;
        }
        return Resultado.OK;
    }

    static String assinar(byte[] subchave, String metodo, String rota, String hora, String nonce, byte[] corpo) {
        String mensagem = metodo + "\n" + rota + "\n" + hora + "\n" + nonce + "\n" + sha256Hex(corpo);
        return HexFormat.of().formatHex(ProvaController.hmacBytes(subchave, mensagem));
    }

    private void limparVencidos(long agora) {
        if (usados.size() > 1_000) {
            usados.values().removeIf(validade -> validade < agora);
        }
    }

    static String sha256Hex(byte[] dados) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(dados == null ? new byte[0] : dados));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponivel", e);
        }
    }
}
