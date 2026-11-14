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
import java.util.Optional;

import io.github.jjoseffelipe2028.bracos.erros.AcaoIndisponivelException;
import io.github.jjoseffelipe2028.bracos.erros.EntradaInvalidaException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Ticket 048: o Estúdio de cortes com produtor, yt-dlp e agentes FALSOS. 🎬 */
class CortesServiceTest {

    private static final Clock RELOGIO = Clock.fixed(Instant.parse("2020-01-01T00:00:00Z"), ZoneId.of("America/Sao_Paulo"));

    @TempDir
    Path tmp;

    private Path entrada;
    private Path saida;
    private final List<List<String>> comandos = new ArrayList<>();

    @BeforeEach
    void pastas() throws IOException {
        entrada = Files.createDirectory(tmp.resolve("entrada"));
        saida = Files.createDirectory(tmp.resolve("saida"));
        Files.writeString(entrada.resolve("live.mp4"), "video");
        Files.writeString(entrada.resolve("notas.txt"), "nao e video");
    }

    /** O falso "produtor" cria uma legenda; o falso "yt-dlp" cria o vídeo; os agentes respondem texto. */
    private CortesService servico() {
        return new CortesService(entrada.toString(), saida.toString(), tmp.resolve("py/.venv/Scripts/python.exe").toString(),
                "yt-dlp", "claude", "agy", (comando, pasta, limite) -> {
                    comandos.add(comando);
                    if (comando.contains("produtor.py")) {
                        Files.writeString(saida.resolve("2026-11-14-010101-corte-01.srt"), "1\n00:00:01,000 --> 00:00:02,000\noi\n");
                        return "linha 1\n📦 1 de 1 cortes prontos. Painel no cofre: x.md\nC:\\Users\\jose\\segredo\\x.mp4";
                    }
                    if (comando.contains("--no-playlist")) {
                        Files.writeString(entrada.resolve("yt-dQw4w9WgXcQ.mp4"), "baixado");
                        return "";
                    }
                    return comando.get(0).equals("agy") ? "002: ok" : "001: lista final";
                }, RELOGIO);
    }

    private static CortesService.Estado esperarFim(CortesService s) throws InterruptedException {
        for (int i = 0; i < 100 && !s.estado().terminou(); i++) {
            Thread.sleep(20);
        }
        return s.estado();
    }

    @Test
    void listaSoVideos_soNome() {
        List<CortesService.Video> videos = servico().videos();
        assertEquals(1, videos.size());
        assertEquals("live.mp4", videos.get(0).nome());
    }

    @Test
    void videoDaLista_produtorComArgumentosFixos_eDuplaRevisa() throws Exception {
        CortesService s = servico();
        s.iniciar("live.mp4", null, false, "amigo_1", 3, "en", true);
        CortesService.Estado fim = esperarFim(s);
        assertTrue(fim.ok(), fim.passos().toString());
        assertEquals(List.of("produtor.py", "live.mp4", "--arroba", "@amigo_1", "--top", "3", "--idioma", "en"),
                comandos.get(0).subList(1, 9));
        assertEquals(4, comandos.size(), "produtor + 001 + 002 + 001");
        assertEquals("claude", comandos.get(1).get(0));
        assertTrue(comandos.get(1).contains("Read,Glob,Grep,Skill"), "a dupla só lê");
        assertEquals(List.of("agy", "--mode", "plan"), comandos.get(2).subList(0, 3));
        assertEquals("001: lista final", fim.dupla());
        assertTrue(fim.saida().stream().noneMatch(l -> l.contains("segredo")), "caminho do PC não vai para a tela");
        try (var arquivos = Files.list(saida)) {
            assertTrue(arquivos.anyMatch(p -> p.getFileName().toString().endsWith("-escolha-da-dupla.md")));
        }
    }

    @Test
    void link_viraEnderecoLimpo_nomeGeradoAqui_eExigeAutorizacao() throws Exception {
        assertEquals(Optional.of("https://www.youtube.com/watch?v=dQw4w9WgXcQ"),
                CortesService.linkLimpo("https://youtu.be/dQw4w9WgXcQ?t=42"));
        assertEquals(Optional.of("https://www.youtube.com/watch?v=dQw4w9WgXcQ"),
                CortesService.linkLimpo("https://www.youtube.com/live/dQw4w9WgXcQ"));
        for (String ruim : List.of("http://youtu.be/dQw4w9WgXcQ", "https://youtube.com.evil.com/watch?v=dQw4w9WgXcQ",
                "https://youtu.be/dQw4w9WgXcQ --exec calc", "https://www.youtube.com/watch?v=abc", "file:///C:/x.mp4")) {
            assertTrue(CortesService.linkLimpo(ruim).isEmpty(), ruim);
        }
        CortesService s = servico();
        assertThrows(EntradaInvalidaException.class, () -> s.iniciar(null, "https://youtu.be/dQw4w9WgXcQ", false, "amigo", 3, "", false));
        s.iniciar(null, "https://youtu.be/dQw4w9WgXcQ", true, "amigo", 3, "", false);
        assertTrue(esperarFim(s).ok());
        List<String> download = comandos.get(0);
        assertEquals("yt-dlp", download.get(0));
        assertEquals(List.of("--", "https://www.youtube.com/watch?v=dQw4w9WgXcQ"), download.subList(download.size() - 2, download.size()));
        assertTrue(download.contains("--no-exec") && download.contains("--no-config"));
        assertEquals("yt-dQw4w9WgXcQ.mp4", comandos.get(1).get(2), "o produtor recebe o nome gerado aqui");
    }

    @Test
    void entradasRuins_400() {
        CortesService s = servico();
        assertThrows(EntradaInvalidaException.class, () -> s.iniciar("../live.mp4", null, false, "amigo", 3, "", false));
        assertThrows(EntradaInvalidaException.class, () -> s.iniciar("notas.txt", null, false, "amigo", 3, "", false));
        assertThrows(EntradaInvalidaException.class, () -> s.iniciar("live.mp4", null, false, "amigo; rm -rf", 3, "", false));
        assertThrows(EntradaInvalidaException.class, () -> s.iniciar("live.mp4", null, false, "amigo", 21, "", false));
        assertThrows(EntradaInvalidaException.class, () -> s.iniciar("live.mp4", null, false, "amigo", 3, "fr", false));
        assertThrows(EntradaInvalidaException.class,
                () -> s.iniciar("live.mp4", "https://youtu.be/dQw4w9WgXcQ", true, "amigo", 3, "", false), "os dois juntos");
        assertTrue(comandos.isEmpty(), "nada rodou");
    }

    @Test
    void produtorComErro_paraEMostraSoOFimLimpo_eLibera() throws Exception {
        CortesService s = new CortesService(entrada.toString(), saida.toString(), "", "yt-dlp", "claude", "agy",
                (comando, pasta, limite) -> {
                    throw new Processos.SaidaComErro("começou\n❌ Não achei o FFmpeg em C:\\Users\\jose\\ffmpeg.exe");
                }, RELOGIO);
        s.iniciar("live.mp4", null, false, "amigo", 3, "", true);
        CortesService.Estado fim = esperarFim(s);
        assertFalse(fim.ok());
        assertEquals("falhou", fim.etapa());
        assertTrue(fim.saida().get(1).startsWith("❌ Não achei o FFmpeg em <pasta>"), fim.saida().toString());
        Thread.sleep(50);
        s.iniciar("live.mp4", null, false, "amigo", 3, "", false); // liberou
    }

    @Test
    void semPastas_503() {
        CortesService s = new CortesService("", "", "", "yt-dlp", "claude", "agy", (c, p, l) -> "", RELOGIO);
        assertThrows(AcaoIndisponivelException.class, s::videos);
        assertThrows(AcaoIndisponivelException.class, () -> s.iniciar("live.mp4", null, false, "amigo", 3, "", false));
    }
}
