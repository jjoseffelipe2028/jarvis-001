package io.github.jjoseffelipe2028.bracos.seguranca;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import io.github.jjoseffelipe2028.bracos.RotasDoJava;
import org.junit.jupiter.api.Test;

/**
 * Ticket 082: teste-guarda de ARQUITETURA. 🏛️
 * As regras de ouro do CLAUDE.md viram teste: se alguém (pessoa ou IA) quebrar uma, o build quebra.
 * 1) Nada escuta fora de 127.0.0.1.
 * 2) Programa externo só nas classes já revisadas, sem shell (cmd/powershell/bash) e sem Runtime.exec.
 * 3) Rota só da allowlist: rota nova obriga a pessoa a vir aqui e acrescentar (revisão consciente).
 *    Nada de rota curinga (*, aceitar qualquer verbo) nem variável de caminho fora da lista.
 * 4) O código Java não lê o arquivo de segredos: o Spring recebe os valores por propriedade.
 * O texto é lido SEM os comentários (comentário pode citar a regra sem quebrá-la).
 */
class ArquiteturaGuardaTest {

    private static final Path FONTES = Path.of("src", "main", "java");
    private static final Path RECURSOS = Path.of("src", "main", "resources");

    /** Allowlist: TODAS as rotas da API. Rota nova? Acrescente aqui de propósito, com o número do ticket. */
    static final Set<String> ROTAS = Set.of(
            "GET /acoes/hora", "GET /acoes/prova",
            "POST /acoes/daily", "GET /acoes/pendencias", "GET /acoes/git-status",
            "POST /acoes/video/avaliar", "POST /acoes/video/relatorio",
            "GET /acoes/sentinela/portas", "GET /acoes/sentinela/integridade", "GET /acoes/sentinela/isca",
            "GET /acoes/sentinela/alarme", // Ticket 080
            "POST /acoes/sentinela/golpe", "POST /acoes/sentinela/proposta",
            "POST /central/sessao", "GET /central/acoes/eventos",
            "GET /central/acoes/pendencias", "GET /central/acoes/git-status", "POST /central/acoes/daily",
            "GET /central/acoes/sentinela/portas", "GET /central/acoes/sentinela/integridade",
            "GET /central/acoes/sentinela/isca", "POST /central/acoes/sentinela/golpe",
            "POST /central/acoes/sentinela/proposta", "POST /central/acoes/chat",
            "GET /central/acoes/estudio", "POST /central/acoes/estudio",
            "GET /central/acoes/cortes", "POST /central/acoes/cortes", "GET /central/acoes/cortes/videos",
            "GET /central/acoes/brabo", "POST /central/acoes/brabo", "GET /central/acoes/brabo/cortes",
            "GET /central/acoes/brabo/video/{nome}");

    /** Única variável de caminho aceita: o nome do vídeo é conferido contra a lista da pasta (Ticket 052). */
    static final Set<String> COM_VARIAVEL = Set.of("GET /central/acoes/brabo/video/{nome}");

    /** Únicas classes que podem chamar programa externo (comando fixo, sem shell, revisadas). */
    static final Set<String> PODEM_EXECUTAR = Set.of("Processos.java", "GitStatusService.java", "ExecutorFixo.java");

    /** "0.0.0.0" só aparece no leitor do netstat, que LÊ quem escuta na rede (não abre nada). */
    static final Set<String> PODEM_CITAR_0000 = Set.of("LeitorDeNetstat.java");

    /** Única citação do arquivo de segredos: a regra que PROÍBE os agentes de lê-lo (deny). */
    static final Set<String> PODEM_CITAR_SEGREDOS = Set.of("EstudioService.java");

    /** Variáveis de ambiente que o código pode ler: só onde ficam os programas do sistema (nada secreto). */
    static final Set<String> VARIAVEIS_DO_SISTEMA = Set.of("\"PATH\"", "\"SystemRoot\"");

    private static final Set<String> SHELLS = Set.of("cmd", "cmd.exe", "powershell", "powershell.exe", "pwsh",
            "pwsh.exe", "sh", "bash", "bash.exe", "/bin/sh", "/bin/bash", "wsl", "wsl.exe");
    private static final Pattern TEXTO = Pattern.compile("\"((?:[^\"\\\\]|\\\\.)*)\"");
    private static final String ARQUIVO_SEGREDOS = "." + "env"; // montado: este teste também não cita o nome cru

    // ---------- 1) só 127.0.0.1 ----------

    @Test
    void apiSoEscutaEm127() throws IOException {
        List<String> enderecos = new ArrayList<>();
        try (Stream<Path> arquivos = Files.walk(RECURSOS)) {
            for (Path p : arquivos.filter(Files::isRegularFile).toList()) {
                for (String linha : Files.readAllLines(p, StandardCharsets.UTF_8)) {
                    String l = linha.strip();
                    if (l.startsWith("server.address") || l.startsWith("address:")) {
                        enderecos.add(p.getFileName() + ": " + l);
                    }
                }
            }
        }
        assertEquals(List.of("application.properties: server.address=127.0.0.1"), enderecos,
                "a API precisa escutar SÓ em 127.0.0.1, num lugar só");
    }

    @Test
    void codigoNaoAbrePortaPorContaPropria() throws IOException {
        for (Map.Entry<Path, String> e : codigo().entrySet()) {
            String nome = e.getKey().getFileName().toString();
            String c = e.getValue();
            for (String proibido : List.of("new ServerSocket", "ServerSocketChannel", "HttpServer.create",
                    "DatagramSocket", "setAddress(")) {
                assertTrue(!c.contains(proibido), nome + ": abre porta por conta própria (" + proibido + ")");
            }
            if (!PODEM_CITAR_0000.contains(nome)) {
                assertTrue(!c.contains("\"0.0.0.0") && !c.contains("\"[::]") && !c.contains("\"::\""),
                        nome + ": endereço que escuta na rede toda");
            }
        }
    }

    // ---------- 2) programa externo ----------

    @Test
    void programaExternoSoNasClassesRevisadas_eSemShell() throws IOException {
        for (Map.Entry<Path, String> e : codigo().entrySet()) {
            String nome = e.getKey().getFileName().toString();
            String c = e.getValue();
            assertTrue(!c.contains("Runtime.getRuntime()") && !c.contains(".exec("),
                    nome + ": Runtime.exec é proibido (comando livre)");
            if (c.contains("ProcessBuilder")) {
                assertTrue(PODEM_EXECUTAR.contains(nome),
                        nome + ": ProcessBuilder fora das classes revisadas " + PODEM_EXECUTAR);
            }
            Matcher m = TEXTO.matcher(c);
            while (m.find()) {
                String t = m.group(1).toLowerCase(Locale.ROOT);
                assertTrue(!SHELLS.contains(t) && !t.endsWith("\\cmd.exe") && !t.endsWith("\\powershell.exe"),
                        nome + ": chama um shell (\"" + m.group(1) + "\"): o texto viraria comando");
            }
        }
    }

    // ---------- 3) rotas ----------

    @Test
    void rotasSaoExatamenteAsDaAllowlist() throws Exception {
        Set<String> doJava = new TreeSet<>(RotasDoJava.mapa().keySet());
        Set<String> novas = new TreeSet<>(doJava);
        novas.removeAll(ROTAS);
        Set<String> sumiram = new TreeSet<>(ROTAS);
        sumiram.removeAll(doJava);
        assertEquals(Set.of(), novas, "rota nova fora da allowlist: revise e acrescente em ROTAS");
        assertEquals(Set.of(), sumiram, "rota da allowlist sumiu: tire de ROTAS também");
    }

    @Test
    void nenhumaRotaCuringa() throws Exception {
        for (String rota : RotasDoJava.mapa().keySet()) {
            assertTrue(!rota.startsWith("ANY "), rota + ": aceita qualquer verbo HTTP");
            assertTrue(!rota.contains("*"), rota + ": rota curinga");
            assertTrue(rota.contains(" /acoes/") || rota.contains(" /central/"), rota + ": fora de /acoes e /central");
            if (rota.contains("{")) {
                assertTrue(COM_VARIAVEL.contains(rota), rota + ": variável de caminho fora da lista");
            }
        }
    }

    // ---------- 4) segredos ----------

    @Test
    void codigoNaoLeOArquivoDeSegredos() throws IOException {
        for (Map.Entry<Path, String> e : codigo().entrySet()) {
            String nome = e.getKey().getFileName().toString();
            String c = e.getValue();
            assertTrue(!c.contains("Dotenv"), nome + ": biblioteca que lê o arquivo de segredos");
            if (!PODEM_CITAR_SEGREDOS.contains(nome)) {
                Matcher m = TEXTO.matcher(c);
                while (m.find()) {
                    assertTrue(!m.group(1).contains(ARQUIVO_SEGREDOS), nome + ": cita o arquivo de segredos no código");
                }
            }
            // Variável de ambiente: só as do sistema para ACHAR programas (git, netstat). Segredo chega por @Value.
            Matcher env = Pattern.compile("System\\.getenv\\(([^)]*)\\)").matcher(c);
            while (env.find()) {
                assertTrue(VARIAVEIS_DO_SISTEMA.contains(env.group(1).strip()),
                        nome + ": System.getenv(" + env.group(1) + ") fora da lista " + VARIAVEIS_DO_SISTEMA);
            }
        }
    }

    // ---------- o próprio guarda funciona? ----------

    @Test
    void tiraComentarioMasNaoTiraTexto() {
        String fonte = "// Runtime.getRuntime()\nString u = \"http://127.0.0.1\"; /* \"0.0.0.0\" */ int x;";
        String limpo = semComentarios(fonte);
        assertTrue(!limpo.contains("Runtime") && !limpo.contains("0.0.0.0"));
        assertTrue(limpo.contains("\"http://127.0.0.1\""), "// dentro de texto não é comentário");
    }

    // ---------- leitura do código ----------

    static Map<Path, String> codigo() throws IOException {
        Map<Path, String> mapa = new java.util.LinkedHashMap<>();
        try (Stream<Path> arquivos = Files.walk(FONTES)) {
            for (Path p : arquivos.filter(a -> a.toString().endsWith(".java")).toList()) {
                mapa.put(p, semComentarios(Files.readString(p, StandardCharsets.UTF_8)));
            }
        }
        assertTrue(mapa.size() > 50, "não achou o código: rode da pasta java/bracos");
        return mapa;
    }

    /** Tira // e /* *\/, respeitando texto entre aspas (ex.: "http://..."). */
    static String semComentarios(String fonte) {
        StringBuilder sb = new StringBuilder(fonte.length());
        int i = 0;
        while (i < fonte.length()) {
            char ch = fonte.charAt(i);
            if (ch == '"' || ch == '\'') {
                int fim = i + 1;
                while (fim < fonte.length() && fonte.charAt(fim) != ch && fonte.charAt(fim) != '\n') {
                    fim += fonte.charAt(fim) == '\\' ? 2 : 1;
                }
                fim = Math.min(fim + 1, fonte.length());
                sb.append(fonte, i, fim);
                i = fim;
            } else if (fonte.startsWith("//", i)) {
                int fim = fonte.indexOf('\n', i);
                i = fim < 0 ? fonte.length() : fim;
            } else if (fonte.startsWith("/*", i)) {
                int fim = fonte.indexOf("*/", i + 2);
                i = fim < 0 ? fonte.length() : fim + 2;
            } else {
                sb.append(ch);
                i++;
            }
        }
        return sb.toString();
    }
}
