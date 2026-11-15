package io.github.jjoseffelipe2028.bracos.acoes.estudio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import io.github.jjoseffelipe2028.bracos.erros.EntradaInvalidaException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Ticket 052: vídeo do apresentador com brabo.py e agentes FALSOS. 🎙️ */
class BraboServiceTest {

    private static final Clock RELOGIO = Clock.fixed(Instant.parse("2026-11-14T15:00:00Z"), ZoneId.of("America/Sao_Paulo"));
    private static final String ROTEIRO = "Aqui vai:\n```json\n{\"titulo\": \"Chuveiro\", \"corte\": \"../segredo.mp4\", "
            + "\"falas\": [{\"em\": 0, \"texto\": \"Desliga o disjuntor.\"}]}\n```";

    @TempDir
    Path tmp;

    private Path pasta;
    private final List<List<String>> comandos = new ArrayList<>();
    private int falhasDoBrabo;
    private int fotos = 3;

    @BeforeEach
    void pastas() throws IOException {
        pasta = Files.createDirectory(tmp.resolve("cortes"));
        Files.writeString(pasta.resolve("2026-11-14-034152-corte-01.mp4"), "corte");
        Files.writeString(pasta.resolve("2026-11-14-034152-corte-01-brabo.mp4"), "ja pronto");
        Files.writeString(pasta.resolve("2026-11-14-034200-corte-02.mp4"), "corte");
    }

    /** O falso brabo.py cria o vídeo e o .post.txt com o nome que o de verdade usaria. */
    private BraboService servico() {
        return new BraboService(pasta.toString(), tmp.resolve("py/.venv/Scripts/python.exe").toString(), "claude", "agy",
                (comando, onde, limite) -> {
                    comandos.add(comando);
                    if (comando.get(0).equals("ffmpeg")) {
                        // o falso ffmpeg tira 3 fotos na pasta pedida (último argumento: QUADROS-…/q-%02d.jpg)
                        Path destino = pasta.resolve(comando.get(comando.size() - 1)).getParent();
                        for (int i = 1; i <= fotos; i++) {
                            Files.writeString(destino.resolve(String.format("q-%02d.jpg", i)), "jpg");
                        }
                        return "";
                    }
                    if (comando.contains("brabo.py")) {
                        if (falhasDoBrabo-- > 0) {
                            throw new Processos.SaidaComErro("❌ A fala termina em 40.0 s, mas o corte tem 25.0 s.");
                        }
                        String json = comando.get(3);
                        String base = json.substring(0, json.length() - ".json".length());
                        String suf = base.endsWith(".mestre") ? "mestre" : "brabo";
                        String video = base.substring(0, base.length() - suf.length() - 1) + "-" + suf;
                        Files.writeString(pasta.resolve(video + ".mp4"), "mp4");
                        Files.writeString(pasta.resolve(video + ".post.txt"), "Título: Chuveiro\n[ ] sintético = SIM\n");
                        return "✅ " + video + ".mp4";
                    }
                    return comando.get(0).equals("agy") ? "002: encurta a fala 2" : ROTEIRO;
                }, RELOGIO);
    }

    private static BraboService.Estado esperarFim(BraboService s) throws InterruptedException {
        for (int i = 0; i < 150 && !s.estado().terminou(); i++) {
            Thread.sleep(20);
        }
        return s.estado();
    }

    @Test
    void mestre_duplaEscreveRevisaFecha_eVideoAparece() throws Exception {
        BraboService s = servico();
        s.iniciar("mestre", "Como trocar a resistência do chuveiro!", (String) null, null, true);
        BraboService.Estado fim = esperarFim(s);
        assertTrue(fim.ok(), fim.passos().toString());
        assertEquals("2026-11-14-120000-como-trocar-a-resistencia-do-c-mestre.mp4", fim.video());
        assertTrue(fim.post().contains("sintético = SIM"));
        assertEquals(List.of("claude", "agy", "claude"), comandos.stream().filter(c -> !c.contains("brabo.py")).map(c -> c.get(0)).toList());
        // o tema NUNCA vira argumento: vai no arquivo PEDIDO
        assertFalse(comandos.stream().flatMap(List::stream).anyMatch(a -> a.contains("chuveiro") && !a.endsWith(".json")));
        assertTrue(Files.readString(pasta.resolve("PEDIDO-2026-11-14-120000.md")).contains("resistência do chuveiro"));
        // campos de confiança forçados pelo Java (o "corte" que a 001 inventou some)
        String roteiro = Files.readString(pasta.resolve("2026-11-14-120000-como-trocar-a-resistencia-do-c.mestre.json"));
        assertTrue(roteiro.contains("\"modo\" : \"ensino\"") && roteiro.contains("\"fundo\" : \"cartoes\""), roteiro);
        assertFalse(roteiro.contains("segredo"));
        assertTrue(s.video(fim.video()).isPresent());
    }

    @Test
    void brabo_forcaCorteECredito_semDupla() throws Exception {
        BraboService s = servico();
        s.iniciar("brabo", "vacilo de moto", "2026-11-14-034152-corte-01.mp4", "amigo_1", false);
        BraboService.Estado fim = esperarFim(s);
        assertTrue(fim.ok(), fim.passos().toString());
        assertEquals(List.of("ffmpeg", "claude"), comandos.stream().filter(c -> !c.contains("brabo.py")).map(c -> c.get(0)).toList());
        // Ticket 053: o Brabo VÊ a jogada (as fotos estão citadas no PEDIDO)
        assertTrue(Files.readString(pasta.resolve("PEDIDO-2026-11-14-120000.md")).contains("QUADROS-2026-11-14-120000/"));
        assertTrue(Files.isRegularFile(pasta.resolve("QUADROS-2026-11-14-120000/q-03.jpg")));
        assertTrue(comandos.get(0).containsAll(List.of("-nostdin", "-i", "2026-11-14-034152-corte-01.mp4")));
        String roteiro = Files.readString(pasta.resolve("2026-11-14-120000-vacilo-de-moto.brabo.json"));
        assertTrue(roteiro.contains("\"corte\" : \"2026-11-14-034152-corte-01.mp4\"") && roteiro.contains("\"credito\" : \"@amigo_1\""));
        assertEquals(List.of(tmp.resolve("py/.venv/Scripts/python.exe").toAbsolutePath().normalize().toString(), "brabo.py", "fazer",
                "2026-11-14-120000-vacilo-de-moto.brabo.json"), comandos.get(comandos.size() - 1));
    }

    @Test
    void roteiroRecusado_001CorrigeUmaVez() throws Exception {
        falhasDoBrabo = 1;
        BraboService s = servico();
        s.iniciar("mestre", "torneira pingando", (String) null, null, false);
        BraboService.Estado fim = esperarFim(s);
        assertTrue(fim.ok(), fim.passos().toString());
        assertEquals("2026-11-14-120000-torneira-pingando-2-mestre.mp4", fim.video());
    }

    @Test
    void roteiroRecusadoDuasVezes_falhaMostrandoOAviso() throws Exception {
        falhasDoBrabo = 2;
        BraboService s = servico();
        s.iniciar("mestre", "torneira pingando", (String) null, null, false);
        BraboService.Estado fim = esperarFim(s);
        assertFalse(fim.ok());
        assertTrue(fim.saida().toString().contains("A fala termina"), fim.saida().toString());
    }

    @Test
    void entradasRuins_saoRecusadas_eNadaRoda() {
        BraboService s = servico();
        assertThrows(EntradaInvalidaException.class, () -> s.iniciar("gemini", "tema", (String) null, null, true));
        assertThrows(EntradaInvalidaException.class, () -> s.iniciar("mestre", "   ", (String) null, null, true));
        assertThrows(EntradaInvalidaException.class, () -> s.iniciar("brabo", "tema", "../fora.mp4", "amigo", true));
        assertThrows(EntradaInvalidaException.class, () -> s.iniciar("brabo", "tema", "2026-11-14-034152-corte-01.mp4", "a b", true));
        assertTrue(comandos.isEmpty());
    }

    @Test
    void listaEVideo_soNomesSeguros() {
        BraboService s = servico();
        assertEquals(List.of("2026-11-14-034200-corte-02.mp4", "2026-11-14-034152-corte-01.mp4"), s.cortes());
        assertTrue(s.video("2026-11-14-034152-corte-01-brabo.mp4").isPresent());
        for (String ruim : List.of("2026-11-14-034152-corte-01.mp4", "../x-brabo.mp4", "..\\x-brabo.mp4", ".x-brabo.mp4", "nao-existe-mestre.mp4")) {
            assertTrue(s.video(ruim).isEmpty(), ruim);
        }
    }

    @Test
    void lote_brabComentaDoisCortes_eSemFotosSegue() throws Exception {
        fotos = 0; // ffmpeg sem fotos: o Brabo segue pela legenda
        BraboService s = servico();
        s.iniciar("brabo", "jogada", List.of("2026-11-14-034200-corte-02.mp4", "2026-11-14-034152-corte-01.mp4"), "amigo_1", false);
        BraboService.Estado fim = esperarFim(s);
        assertTrue(fim.ok(), fim.passos().toString());
        assertEquals(List.of("2026-11-14-120000-01-jogada-brabo.mp4", "2026-11-14-120000-02-jogada-brabo.mp4"), fim.videos());
        assertTrue(fim.passos().stream().anyMatch(p -> p.contains("Sem fotos")));
        assertFalse(Files.readString(pasta.resolve("PEDIDO-2026-11-14-120000-01.md")).contains("QUADROS"));
    }

    @Test
    void lote_umCorteFalha_osOutrosSeguem() throws Exception {
        falhasDoBrabo = 2; // o 1º corte falha nas 2 chances; o 2º sai
        BraboService s = servico();
        s.iniciar("brabo", "jogada", List.of("2026-11-14-034200-corte-02.mp4", "2026-11-14-034152-corte-01.mp4"), "amigo_1", false);
        BraboService.Estado fim = esperarFim(s);
        assertTrue(fim.ok(), fim.passos().toString());
        assertEquals(List.of("2026-11-14-120000-02-jogada-brabo.mp4"), fim.videos());
        assertTrue(fim.passos().stream().anyMatch(p -> p.contains("corte 1 não deu certo")));
    }

    @Test
    void lote_repetidoForaDaListaOuGrande_recusado() {
        BraboService s = servico();
        String c1 = "2026-11-14-034152-corte-01.mp4";
        assertThrows(EntradaInvalidaException.class, () -> s.iniciar("brabo", "t", List.of(c1, c1), "amigo", true));
        assertThrows(EntradaInvalidaException.class, () -> s.iniciar("brabo", "t", List.of(c1, "../x.mp4"), "amigo", true));
        assertThrows(EntradaInvalidaException.class, () -> s.iniciar("brabo", "t", List.<String>of(), "amigo", true));
        assertTrue(comandos.isEmpty());
    }

    @Test
    void apelido_curtoESemAcento() {
        assertEquals("trocar-a-resistencia", BraboService.apelido("Trocar a resistência!"));
        assertEquals("video", BraboService.apelido("🔧🔧"));
    }
}
