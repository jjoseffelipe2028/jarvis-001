package io.github.jjoseffelipe2028.bracos.acoes.estudio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.github.jjoseffelipe2028.bracos.erros.AcaoIndisponivelException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Ticket 047: o Estúdio com agentes FALSOS (nada de Claude nem Antigravity de verdade). 🎮 */
class EstudioServiceTest {

    private static final Clock RELOGIO = Clock.fixed(Instant.parse("2026-11-13T06:00:00Z"), ZoneId.of("America/Sao_Paulo"));

    @TempDir
    Path raiz;

    private final List<List<String>> comandos = new ArrayList<>();

    private EstudioService estudio(EstudioService.Executor executor) {
        return new EstudioService(raiz.toString(), "claude", "agy", executor, RELOGIO);
    }

    private EstudioService.Executor falso(String... respostas) {
        List<String> fila = new ArrayList<>(List.of(respostas));
        return (comando, pasta, limite) -> {
            comandos.add(comando);
            return fila.remove(0);
        };
    }

    private static EstudioService.Estado esperarFim(EstudioService s) throws InterruptedException {
        for (int i = 0; i < 100 && !s.estado().terminou(); i++) {
            Thread.sleep(20);
        }
        return s.estado();
    }

    @Test
    void cicloCompleto_001planeja_002revisa_001constroi_eGravaTudoNaPastaNova() throws Exception {
        EstudioService s = estudio(falso("plano bom", "falta game over", "Fiz o jogo. Aceitei o game over da 002."));
        EstudioService.Estado inicio = s.iniciar("Um mini jogo de NAVE, com 3 vidas!", true);
        assertEquals("20261113-030000-um-mini-jogo-de-nave-com-3-vid", inicio.id());

        EstudioService.Estado fim = esperarFim(s);
        assertTrue(fim.ok());
        assertEquals("pronto", fim.etapa());
        assertEquals("Fiz o jogo. Aceitei o game over da 002.", fim.resumo());
        Path pasta = raiz.resolve("projetos").resolve(inicio.id());
        assertTrue(Files.readString(pasta.resolve("PEDIDO.md")).contains("Um mini jogo de NAVE, com 3 vidas!"));
        assertTrue(Files.readString(pasta.resolve("PLANO.md")).contains("plano bom"));
        assertTrue(Files.readString(pasta.resolve("REVISAO.md")).contains("falta game over"));
        assertTrue(Files.readString(pasta.resolve("RESUMO.md")).contains("Fiz o jogo"));

        assertEquals(3, comandos.size());
        assertEquals("claude", comandos.get(0).get(0));
        assertTrue(comandos.get(0).contains("Read,Glob,Grep,Skill"), "no plano a 001 só lê");
        assertEquals(List.of("agy", "--mode", "plan"), comandos.get(1).subList(0, 3), "a 002 só analisa");
        assertTrue(comandos.get(2).contains("Read,Write,Edit,Glob,Grep,Skill"));
    }

    @Test
    void comandosSaoFixos_oPedidoNuncaViraArgumento_eNaoTemTerminalNemInternet() throws Exception {
        EstudioService s = estudio(falso("p", "r", "ok"));
        s.iniciar("jogo\"; rm -rf / & calc.exe --dangerously-skip-permissions", true);
        esperarFim(s);
        for (List<String> comando : comandos) {
            for (String arg : comando) {
                assertFalse(arg.contains("rm -rf") || arg.contains("calc.exe") || arg.contains("dangerously"), arg);
            }
        }
        for (List<String> c : List.of(comandos.get(0), comandos.get(2))) {
            assertTrue(c.contains("--strict-mcp-config"));
            assertEquals("Bash,PowerShell,WebFetch,WebSearch", c.get(c.indexOf("--disallowedTools") + 1));
            assertEquals("dontAsk", c.get(c.indexOf("--permission-mode") + 1));
            Path regras = Path.of(c.get(c.indexOf("--settings") + 1));
            assertFalse(regras.startsWith(raiz), "as regras ficam fora da pasta onde a 001 escreve");
            String deny = Files.readString(regras, StandardCharsets.UTF_8);
            assertTrue(deny.contains("Read(**/.env)") && deny.contains("Write(.claude/**)"));
        }
    }

    @Test
    void semRevisao_pulaA002() throws Exception {
        EstudioService s = estudio(falso("p", "ok"));
        s.iniciar("jogo da cobrinha", false);
        assertTrue(esperarFim(s).ok());
        assertEquals(2, comandos.size());
        assertFalse(Files.exists(raiz.resolve("projetos").resolve(s.estado().id()).resolve("REVISAO.md")));
    }

    @Test
    void umPedidoPorVez_eDepoisLibera() throws Exception {
        CountDownLatch solta = new CountDownLatch(1);
        EstudioService s = estudio((comando, pasta, limite) -> {
            solta.await(5, TimeUnit.SECONDS);
            return "ok";
        });
        s.iniciar("jogo um", false);
        assertThrows(AcaoIndisponivelException.class, () -> s.iniciar("jogo dois", false));
        solta.countDown();
        assertTrue(esperarFim(s).ok());
        Thread.sleep(50);
        s.iniciar("jogo tres", false); // não lança: liberou
    }

    @Test
    void agenteQueFalha_paraNoPasso_semDerrubar_eLibera() throws Exception {
        EstudioService s = estudio((comando, pasta, limite) -> {
            throw new IOException("C:\\Users\\segredo\\caminho");
        });
        s.iniciar("jogo", true);
        EstudioService.Estado fim = esperarFim(s);
        assertTrue(fim.terminou());
        assertFalse(fim.ok());
        assertEquals("falhou", fim.etapa());
        assertFalse(String.join(" ", fim.passos()).contains("segredo"), "o erro cru não vai para a tela");
        Thread.sleep(50);
        s.iniciar("outro jogo", false);
    }

    @Test
    void semPasta_503() {
        EstudioService s = new EstudioService(raiz.resolve("nao-existe").toString(), "claude", "agy", falso(), RELOGIO);
        assertThrows(AcaoIndisponivelException.class, () -> s.iniciar("jogo", false));
        EstudioService vazio = new EstudioService("", "claude", "agy", falso(), RELOGIO);
        assertThrows(AcaoIndisponivelException.class, () -> vazio.iniciar("jogo", false));
    }

    @Test
    void nomeDaPasta_soLetrasSimples_eResumoLimpo() {
        assertEquals("jogo", EstudioService.nomeCurto("../../.."));
        assertEquals("cao-e-pao", EstudioService.nomeCurto("Cão e Pão!"));
        assertEquals("jogo", EstudioService.nomeCurto("🚀🚀"));
        assertEquals("ok\nlinha", EstudioService.limparParaTela("ok\u001b\u0007\u202E\nlinha"));
        assertTrue(EstudioService.limparParaTela("a".repeat(5000)).length() <= EstudioService.LIMITE_TELA + 1);
    }
}
