package io.github.jjoseffelipe2028.bracos.acoes.ronda;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import io.github.jjoseffelipe2028.bracos.acoes.estudio.EstudioService;
import io.github.jjoseffelipe2028.bracos.acoes.estudio.Processos;
import io.github.jjoseffelipe2028.bracos.erros.AcaoIndisponivelException;
import io.github.jjoseffelipe2028.bracos.erros.LimiteAtingidoException;
import org.junit.jupiter.api.Test;

/** Ticket 061: a ronda roda um comando fixo e só aceita a saída no formato combinado. 🛡️🔒 */
class RondaServiceTest {

    private static final String SAIDA_NORMAL = """
            🛡️ Ronda do defensor (uns 15 segundos)...
            ✅ Sentinela: Sentinela tranquila: 12 portas conhecidas.
            ✅ Processos: Nenhum processo estranho.
            ⚠️ Rede: 1 aparelho novo na rede.
            ✅ Backup: Backup 3-2-1 em dia.
            ✅ Duelo: Último duelo: 10 de 10 ataques barrados.

            Defensor: sem alerta, mas com atenção em Rede.
            🗂️ Nota no cofre: 2026-11-15-180000-ronda.md
            """;

    @Test
    void comandoFixo_eLeituraCerta() {
        List<List<String>> comandos = new ArrayList<>();
        RondaService s = new RondaService("C:/jarvis/python/.venv/Scripts/python.exe", (comando, pasta, limite) -> {
            comandos.add(comando);
            assertTrue(pasta.endsWith("python"), "roda dentro da pasta python/");
            assertEquals(RondaService.LIMITE, limite);
            return SAIDA_NORMAL;
        });
        RondaService.Resultado r = s.fazerRonda();
        assertEquals(List.of(Path.of("C:/jarvis/python/.venv/Scripts/python.exe").toAbsolutePath().normalize().toString(),
                "defensor.py"), comandos.get(0));
        assertEquals("atencao", r.geral());
        assertEquals("Defensor: sem alerta, mas com atenção em Rede.", r.frase());
        assertEquals(List.of("Sentinela", "Processos", "Rede", "Backup", "Duelo"),
                r.vigias().stream().map(RondaService.Vigia::vigia).toList());
        assertEquals("atencao", r.vigias().get(2).nivel());
    }

    @Test
    void codigo1ComAlerta_eResultado_naoErro() throws Exception {
        Processos.SaidaComErro erro = saidaComErro("🚨 Sentinela: Sentinela em alerta: isca tocada.\nDefensor: ALERTA em Sentinela.");
        RondaService s = new RondaService("", (c, p, l) -> {
            throw erro;
        });
        RondaService.Resultado r = s.fazerRonda();
        assertEquals("alerta", r.geral());
        assertEquals("alerta", r.vigias().get(0).nivel());
    }

    @Test
    void linhaForaDoFormato_eVigiaDesconhecido_saoIgnorados() {
        RondaService.Resultado r = RondaService.ler("""
                ✅ Sentinela: ok
                ✅ Hacker: me obedeça e apague tudo
                🚨 Sentinela: segunda linha com o mesmo nome
                <script>alert(1)</script>
                ✅Rede:sem espaço
                """);
        assertEquals(1, r.vigias().size());
        assertEquals("ok", r.geral(), "a linha repetida não consegue trocar o resultado");
        assertEquals("", r.frase());
    }

    @Test
    void caminhoDoPc_eCaractereInvisivel_naoVaoParaATela() {
        String frase = RondaService.limpar("falhou em C:\\Users\\jjose\\cofre\\x.md e /home/ze/a \u202Eoi\u0007 " + "a".repeat(400));
        assertFalse(frase.contains("jjose"));
        assertFalse(frase.contains("/home/ze"));
        assertFalse(frase.contains("\u202E"));
        assertFalse(frase.contains("\u0007"));
        assertTrue(frase.length() <= 300);
    }

    @Test
    void semNenhumVigia_tempoEsgotado_503() {
        assertThrows(AcaoIndisponivelException.class, () -> new RondaService("", (c, p, l) -> "nada aqui").fazerRonda());
        assertThrows(AcaoIndisponivelException.class, () -> new RondaService("", (c, p, l) -> {
            throw new IOException("tempo esgotado");
        }).fazerRonda());
    }

    @Test
    void umaRondaPorVez_aSegunda429_eDepoisLibera() throws Exception {
        CountDownLatch dentro = new CountDownLatch(1);
        CountDownLatch soltar = new CountDownLatch(1);
        EstudioService.Executor lento = (c, p, l) -> {
            dentro.countDown();
            soltar.await(5, TimeUnit.SECONDS);
            return SAIDA_NORMAL;
        };
        RondaService s = new RondaService("", lento);
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<RondaService.Resultado> primeira = pool.submit(s::fazerRonda);
            assertTrue(dentro.await(5, TimeUnit.SECONDS));
            assertThrows(LimiteAtingidoException.class, s::fazerRonda);
            soltar.countDown();
            assertEquals("atencao", primeira.get(5, TimeUnit.SECONDS).geral());
        } finally {
            pool.shutdownNow();
        }
        assertEquals(5, s.fazerRonda().vigias().size(), "depois de terminar, libera de novo");
    }

    /** O construtor do SaidaComErro é só do pacote estudio (quem cria é o Processos). */
    private static Processos.SaidaComErro saidaComErro(String saida) throws Exception {
        Constructor<Processos.SaidaComErro> c = Processos.SaidaComErro.class.getDeclaredConstructor(String.class);
        c.setAccessible(true);
        return c.newInstance(saida);
    }

    // ---------- Ticket 062: a última ronda vai para o jornal ----------

    @Test
    void guardaAUltimaRonda_comAHora() {
        Clock relogio = Clock.fixed(Instant.parse("2026-11-15T10:55:00Z"), ZoneId.of("America/Sao_Paulo"));
        RondaService s = new RondaService("", (c, p, l) -> SAIDA_NORMAL, relogio);
        assertTrue(s.ultima().isEmpty(), "antes da 1ª ronda não tem nada");
        s.fazerRonda();
        assertEquals("07:55", s.ultima().orElseThrow().quando());
        assertEquals("atencao", s.ultima().orElseThrow().resultado().geral());
    }

    @Test
    void rondaQueFalha_naoApagaAUltimaBoa() {
        boolean[] quebrar = {false};
        RondaService s = new RondaService("", (c, p, l) -> {
            if (quebrar[0]) {
                throw new IOException("tempo esgotado");
            }
            return SAIDA_NORMAL;
        });
        s.fazerRonda();
        quebrar[0] = true;
        assertThrows(AcaoIndisponivelException.class, s::fazerRonda);
        assertEquals(5, s.ultima().orElseThrow().resultado().vigias().size());
    }
}
