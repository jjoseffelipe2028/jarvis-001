package io.github.jjoseffelipe2028.bracos.seguranca;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.Deque;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Alarme de tentativas (Ticket 080). 🚨
 * - Olha a RESPOSTA de todo pedido: 401 ou 403 = um pedido negado (por qualquer porteiro, até um novo).
 * - {@value #LIMITE} negados dentro de {@value #JANELA_SEGUNDOS} s = parece alguém adivinhando o token
 *   (MITRE T1110, Brute Force). Conta 1 disparo, escreve 1 linha no log e zera a janela.
 * - Só AVISA: não bloqueia ninguém (bloquear deixaria um atacante trancar o próprio José para fora).
 * - Não guarda token, IP, rota nem corpo: só a HORA de cada negado e contagens.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1) // logo depois dos cabeçalhos de segurança: vê todas as recusas
public class AlarmeDeTentativas extends OncePerRequestFilter {

    public static final int LIMITE = 5;
    public static final long JANELA_SEGUNDOS = 60;
    static final int MAXIMO_DISPAROS = 9_999;

    private static final Duration JANELA = Duration.ofSeconds(JANELA_SEGUNDOS);
    private static final DateTimeFormatter HORA =
            DateTimeFormatter.ofPattern("dd/MM HH:mm").withZone(ZoneId.of("America/Sao_Paulo"));
    private static final Logger log = LoggerFactory.getLogger(AlarmeDeTentativas.class);

    /** O que a Sentinela mostra: só números e a hora do último disparo. */
    public record Estado(int disparos, String ultimoDisparo, int negadosNoMinuto) {
    }

    private final Clock relogio;
    private final Deque<Instant> negados = new ArrayDeque<>(); // nunca passa de LIMITE itens
    private int disparos;
    private Instant ultimo;

    @Autowired
    public AlarmeDeTentativas() {
        this(Clock.systemUTC());
    }

    AlarmeDeTentativas(Clock relogio) {
        this.relogio = relogio;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        chain.doFilter(request, response);
        int status = response.getStatus();
        if (status == 401 || status == 403) {
            negado();
        }
    }

    /** Um pedido foi negado. Devolve true se este negado disparou o alarme. */
    public synchronized boolean negado() {
        Instant agora = relogio.instant();
        limpar(agora);
        negados.addLast(agora);
        if (negados.size() < LIMITE) {
            return false;
        }
        negados.clear(); // zera: o próximo disparo precisa de mais 5 negados
        disparos = Math.min(disparos + 1, MAXIMO_DISPAROS);
        ultimo = agora;
        // Linha fixa: nada do pedido entra aqui (anti log injection e sem dado sensível)
        log.warn("alarme=tentativas negados={} janela={}s disparos={}", LIMITE, JANELA_SEGUNDOS, disparos);
        return true;
    }

    public synchronized Estado estado() {
        limpar(relogio.instant());
        return new Estado(disparos, ultimo == null ? "" : HORA.format(ultimo), negados.size());
    }

    /** Tira da fila os negados com mais de 1 minuto. */
    private void limpar(Instant agora) {
        Instant limite = agora.minus(JANELA);
        while (!negados.isEmpty() && !negados.peekFirst().isAfter(limite)) {
            negados.removeFirst();
        }
    }
}
