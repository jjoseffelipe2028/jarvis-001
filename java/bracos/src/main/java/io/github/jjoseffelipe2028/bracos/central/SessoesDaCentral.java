package io.github.jjoseffelipe2028.bracos.central;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * "Crachás" da Central (Ticket 015): só na memória, somem quando a API desliga. 🔒
 * - Cada crachá vale 12 h. No máximo 5 ao mesmo tempo (o mais velho sai).
 * - O crachá vai num cookie HttpOnly + SameSite=Strict: o JavaScript não lê,
 *   e um site de fora não consegue fazer o navegador enviar.
 */
@Component
public class SessoesDaCentral {

    static final Duration VALIDADE = Duration.ofHours(12);
    static final int MAXIMO = 5;
    private static final SecureRandom SORTEIO = new SecureRandom();

    private final Map<String, Instant> validos = new ConcurrentHashMap<>();
    private final Clock relogio;

    @Autowired
    public SessoesDaCentral() {
        this(Clock.systemUTC());
    }

    SessoesDaCentral(Clock relogio) {
        this.relogio = relogio;
    }

    public synchronized String criar() {
        Instant agora = relogio.instant();
        validos.values().removeIf(expira -> !expira.isAfter(agora));
        while (validos.size() >= MAXIMO) {
            validos.entrySet().stream().min(Comparator.comparing(Map.Entry::getValue))
                    .ifPresent(maisVelho -> validos.remove(maisVelho.getKey()));
        }
        byte[] bytes = new byte[32];
        SORTEIO.nextBytes(bytes);
        String cracha = HexFormat.of().formatHex(bytes);
        validos.put(cracha, agora.plus(VALIDADE));
        return cracha;
    }

    public boolean valido(String cracha) {
        if (cracha == null || cracha.length() != 64) {
            return false;
        }
        Instant expira = validos.get(cracha);
        return expira != null && expira.isAfter(relogio.instant());
    }
}
