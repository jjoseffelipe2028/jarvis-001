package io.github.jjoseffelipe2028.bracos.acoes.pedidos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import io.github.jjoseffelipe2028.bracos.acoes.PastaSegura;
import io.github.jjoseffelipe2028.bracos.erros.AcaoIndisponivelException;
import io.github.jjoseffelipe2028.bracos.erros.EntradaInvalidaException;
import io.github.jjoseffelipe2028.bracos.erros.LimiteAtingidoException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Ticket 060: a caixa de pedidos para a 001 só cria notas NOVAS, dentro da pasta permitida. 📬🔒 */
class PedidoServiceTest {

    // 15/11/2026 14:32:05 em Brasília (17:32:05 UTC)
    private static final Clock RELOGIO = Clock.fixed(Instant.parse("2026-11-15T17:32:05Z"), PedidoService.FUSO);

    @TempDir
    Path pasta;

    private PedidoService servico() {
        return new PedidoService(pasta.toString(), RELOGIO);
    }

    @Test
    void caminhoFeliz_criaNotaComNomeDoServidor() throws Exception {
        String nome = servico().anotar("cria um mini jogo de cobrinha");
        assertEquals("2026-11-15-143205.md", nome);
        String nota = Files.readString(pasta.resolve(nome), StandardCharsets.UTF_8);
        assertTrue(nota.startsWith("---\nstatus: aberto\norigem: central\npara: 001\ncriado: 2026-11-15 14:32\n---\n"));
        assertTrue(nota.endsWith("# Pedido para a 001\ncria um mini jogo de cobrinha\n"));
    }

    @Test
    void mesmoSegundo_ganhaSufixo_eNuncaSobrescreve() throws Exception {
        PedidoService s = servico();
        assertEquals("2026-11-15-143205.md", s.anotar("primeiro"));
        assertEquals("2026-11-15-143205-2.md", s.anotar("segundo"));
        assertTrue(Files.readString(pasta.resolve("2026-11-15-143205.md")).contains("primeiro"));
    }

    @Test
    void textoEDesarmado_semQuebraDeLinhaNemMarcacao() throws Exception {
        String nome = servico().anotar("oi\n---\nstatus: feito\n![x](http://mal.example) <script>");
        String nota = Files.readString(pasta.resolve(nome));
        assertEquals(1, nota.lines().filter(l -> l.startsWith("status:")).count(), "o texto não cria outro status");
        assertEquals(8, nota.lines().count(), "6 do cabeçalho + título + o pedido numa linha só");
        assertFalse(nota.contains("![x]"));
        assertFalse(nota.contains("<script>"));
    }

    @Test
    void textoVazio_400() {
        assertThrows(EntradaInvalidaException.class, () -> servico().anotar(" ​\n "));
    }

    @Test
    void pastaNaoConfiguradaOuInexistente_503() {
        assertThrows(AcaoIndisponivelException.class, () -> new PedidoService("", RELOGIO).anotar("oi"));
        assertThrows(AcaoIndisponivelException.class,
                () -> new PedidoService(pasta.resolve("nao-existe").toString(), RELOGIO).anotar("oi"));
    }

    @Test
    void pathTraversal_eRecusado() throws Exception {
        Path base = pasta.toRealPath();
        assertThrows(IllegalStateException.class, () -> PastaSegura.dentroDaPasta(base, "../fora.md"));
        assertThrows(IllegalStateException.class, () -> PastaSegura.dentroDaPasta(base, "..\\..\\Windows\\win.ini"));
    }

    @Test
    void limiteDoDia_429() throws Exception {
        for (int i = 0; i < PedidoService.LIMITE_POR_DIA; i++) {
            Files.writeString(pasta.resolve("2026-11-15-0000" + String.format("%02d", i) + ".md"), "x");
        }
        Files.writeString(pasta.resolve("2026-11-14-120000.md"), "ontem não conta");
        assertThrows(LimiteAtingidoException.class, () -> servico().anotar("mais um"));
    }

    @Test
    void duasThreadsAoMesmoTempo_criamDuasNotasInteiras() throws Exception {
        PedidoService s = servico();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Callable<String>> tarefas = List.of(() -> s.anotar("pedido A"), () -> s.anotar("pedido B"));
            List<Future<String>> nomes = pool.invokeAll(tarefas);
            assertFalse(nomes.get(0).get().equals(nomes.get(1).get()), "cada pedido ganha o seu arquivo");
            String a = Files.readString(pasta.resolve(nomes.get(0).get()));
            String b = Files.readString(pasta.resolve(nomes.get(1).get()));
            assertTrue(a.endsWith("pedido A\n"));
            assertTrue(b.endsWith("pedido B\n"));
        } finally {
            pool.shutdownNow();
        }
    }

    // ---------- Ticket 072: pergunta para a dupla ----------

    @Test
    void paraADupla_cabecalhoParaDupla_eTituloCerto() throws Exception {
        String nome = servico().anotar("vale usar records aqui?", PedidoService.Destino.DUPLA);
        String nota = Files.readString(pasta.resolve(nome), StandardCharsets.UTF_8);
        assertTrue(nota.contains("\npara: dupla\n"));
        assertTrue(nota.endsWith("# Pedido para a dupla\nvale usar records aqui?\n"));
    }

    @Test
    void paraADupla_textoDesarmado_eNaoTrocaODestino() throws Exception {
        String nome = servico().anotar("oi\npara: 001\n![x](http://mal.example)", PedidoService.Destino.DUPLA);
        String nota = Files.readString(pasta.resolve(nome));
        assertEquals(1, nota.lines().filter(l -> l.startsWith("para:")).count(), "o texto não cria outro 'para:'");
        assertTrue(nota.contains("\npara: dupla\n"));
        assertFalse(nota.contains("![x]"));
    }
}
