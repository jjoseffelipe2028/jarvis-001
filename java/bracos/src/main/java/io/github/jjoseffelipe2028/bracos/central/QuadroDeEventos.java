package io.github.jjoseffelipe2028.bracos.central;

import java.time.Clock;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import org.springframework.stereotype.Component;

/**
 * Quadro de eventos da Central (Ticket 017): as últimas ações que os braços fizeram,
 * para o escritório reagir ao trabalho real. 🔒
 * - Fica só na MEMÓRIA (some quando a API desliga) e guarda no máximo {@link #MAXIMO}.
 * - Cada evento tem só nomes FIXOS (ação, resultado, cliente) e a hora: nunca texto de nota,
 *   caminho de pasta ou corpo de pedido.
 */
@Component
public class QuadroDeEventos {

    public static final int MAXIMO = 30;

    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm:ss");

    /** Um evento. Todos os textos vêm de listas fixas do código, nunca de quem chamou. */
    public record Evento(long seq, String acao, String resultado, String cliente, String hora) {
    }

    /** Resposta da leitura: eventos novos + o número do último, para o próximo pedido. */
    public record Leitura(List<Evento> eventos, long ultimo) {
    }

    private final Deque<Evento> eventos = new ArrayDeque<>();
    private final Clock relogio;
    private long seq;

    public QuadroDeEventos() {
        this(Clock.system(ZoneId.of("America/Sao_Paulo")));
    }

    QuadroDeEventos(Clock relogio) {
        this.relogio = relogio;
    }

    public synchronized void registrar(String acao, String resultado, String cliente) {
        seq++;
        eventos.addLast(new Evento(seq, acao, resultado, cliente, LocalTime.now(relogio).format(HORA)));
        while (eventos.size() > MAXIMO) {
            eventos.removeFirst();
        }
    }

    /** Eventos com número maior que {@code ultimoVisto} (do mais velho para o mais novo). */
    public synchronized Leitura depois(long ultimoVisto) {
        List<Evento> novos = new ArrayList<>();
        for (Evento e : eventos) {
            if (e.seq() > ultimoVisto) {
                novos.add(e);
            }
        }
        return new Leitura(List.copyOf(novos), seq);
    }
}
