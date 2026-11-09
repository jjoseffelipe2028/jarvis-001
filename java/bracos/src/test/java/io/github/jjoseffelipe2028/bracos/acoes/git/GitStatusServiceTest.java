package io.github.jjoseffelipe2028.bracos.acoes.git;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import io.github.jjoseffelipe2028.bracos.acoes.git.GitStatusService.Status;
import io.github.jjoseffelipe2028.bracos.erros.AcaoIndisponivelException;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GitStatusServiceTest {

    @TempDir
    Path pasta;

    // ---------- 1) Leitor da saída: só números, nunca nome de arquivo ----------

    @Test
    void leContagensBranchEAFrenteAtras() {
        Status s = GitStatusService.ler(
                "## main...origin/main [ahead 2, behind 1]\n M a.java\n?? b.txt\n?? c.txt\nA  d.md\n");
        assertEquals(new Status("main", false, 2, 2, 2, 1), s);
    }

    @Test
    void repositorioLimpo() {
        assertEquals(new Status("main", true, 0, 0, 0, 0), GitStatusService.ler("## main...origin/main\n"));
    }

    @Test
    void repositorioSemCommitAinda() {
        Status s = GitStatusService.ler("## No commits yet on main\n?? novo.txt\n");
        assertEquals("main", s.branch());
        assertEquals(1, s.novos());
    }

    @Test
    void crlfDoWindowsTambemFunciona() {
        assertEquals(new Status("main", false, 1, 0, 0, 0), GitStatusService.ler("## main\r\n M a\r\n"));
    }

    @Test
    void nomeDeArquivoNuncaSaiNaResposta() {
        Status s = GitStatusService.ler("## main\n M senhas-secretas.txt\n?? diario-pessoal.md\n");
        assertFalse(s.toString().contains("senhas"));
        assertFalse(s.toString().contains("diario"));
    }

    @Test
    void branchEstranhaNaoSaiComoVeio() {
        assertEquals("feature/ticket-004", GitStatusService.filtrarBranch("feature/ticket-004"));
        assertEquals("?", GitStatusService.filtrarBranch("x$(rm -rf ~)"));
        assertEquals("?", GitStatusService.filtrarBranch("a\u202Eb"));
        assertEquals("?", GitStatusService.filtrarBranch("<script>"));
        assertEquals("?", GitStatusService.filtrarBranch("a".repeat(101)));
        assertEquals("HEAD-solto", GitStatusService.filtrarBranch("HEAD (no branch)"));
    }

    @Test
    void numeroAbsurdoNaoQuebra() {
        Status s = GitStatusService.ler("## main...o/main [ahead 99999999999999999999]\n");
        assertEquals(Integer.MAX_VALUE, s.aFrente());
    }

    // ---------- 2) Travas do processo ----------

    @Test
    void argumentosFixosDesligamFsmonitorESubmodulos() {
        List<String> c = GitStatusService.ARGUMENTOS_GIT;
        assertTrue(c.contains("core.fsmonitor=false"));
        assertTrue(c.contains("--ignore-submodules=all"));
        assertTrue(c.contains("--porcelain=v1"));
    }

    /** Achado 1 do hacker: um git.exe plantado na pasta atual não pode ser usado. */
    @Test
    void resolverGit_ignoraPastaRelativaEGitDentroDoRepo() throws Exception {
        String nome = System.getProperty("os.name").toLowerCase().startsWith("windows") ? "git.exe" : "git";
        Path repo = Files.createDirectory(pasta.resolve("repo"));
        Path plantado = Files.createDirectory(repo.resolve("bin"));
        Files.writeString(plantado.resolve(nome), "falso");
        plantado.resolve(nome).toFile().setExecutable(true);
        Path bom = Files.createDirectory(pasta.resolve("git-bom"));
        Files.writeString(bom.resolve(nome), "bom");
        bom.resolve(nome).toFile().setExecutable(true);

        String path = String.join(java.io.File.pathSeparator, ".", "bin", plantado.toString(), bom.toString());
        assertEquals(java.util.Optional.of(bom.resolve(nome)), GitStatusService.resolverGit(path, repo));
    }

    @Test
    void resolverGit_semGitNoPath_vazio() {
        assertTrue(GitStatusService.resolverGit("", pasta).isEmpty());
        assertTrue(GitStatusService.resolverGit(null, pasta).isEmpty());
        assertTrue(GitStatusService.resolverGit(pasta.resolve("nada").toString(), pasta).isEmpty());
    }

    /** Achado 5 do hacker: um git por vez. O segundo pedido junto recebe 503 na hora. */
    @Test
    void doisPedidosJuntos_oSegundoRecebeOcupado() throws Exception {
        List<String> cmd = programaJava("Lento", "Thread.sleep(60_000);");
        GitStatusService s = new GitStatusService(pasta.toString(), cmd, Duration.ofSeconds(6));

        Thread primeiro = Thread.ofVirtual().start(() -> {
            try {
                s.consultar();
            } catch (AcaoIndisponivelException e) {
                // esperado: demorou demais
            }
        });
        Thread.sleep(1500); // o primeiro já está rodando
        AcaoIndisponivelException e = assertThrows(AcaoIndisponivelException.class, s::consultar);
        assertEquals("git ocupado", e.getMessage());
        primeiro.join(20_000);
    }

    @Test
    void ambienteApagaVariaveisGitDeFora() {
        Map<String, String> amb = new HashMap<>(Map.of(
                "GIT_DIR", "/outro/repo", "git_config_global", "/x", "PATH", "/bin"));
        GitStatusService.limparAmbiente(amb);
        assertFalse(amb.containsKey("GIT_DIR"));
        assertFalse(amb.containsKey("git_config_global"));
        assertEquals("/bin", amb.get("PATH"));
        assertEquals("0", amb.get("GIT_TERMINAL_PROMPT"));
    }

    @Test
    void pastaNaoConfiguradaOuInexistente_503() {
        assertThrows(AcaoIndisponivelException.class, () -> new GitStatusService("").consultar());
        assertThrows(AcaoIndisponivelException.class,
                () -> new GitStatusService(pasta.resolve("nao-existe").toString()).consultar());
    }

    @Test
    void programaQueTrava_eMortoNoTempoLimite() throws Exception {
        List<String> cmd = programaJava("Trava", "Thread.sleep(60_000);");
        GitStatusService s = new GitStatusService(pasta.toString(), cmd, Duration.ofSeconds(4));

        long inicio = System.nanoTime();
        AcaoIndisponivelException e = assertThrows(AcaoIndisponivelException.class, s::consultar);
        long segundos = TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - inicio);

        assertEquals("git demorou demais", e.getMessage());
        assertTrue(segundos < 15, "deveria parar perto do limite, levou " + segundos + " s");
    }

    @Test
    void saidaGiganteERecusada() throws Exception {
        List<String> cmd = programaJava("Gigante",
                "System.out.print(\"x\".repeat(" + (GitStatusService.LIMITE_SAIDA_BYTES * 3) + "));");
        GitStatusService s = new GitStatusService(pasta.toString(), cmd, Duration.ofSeconds(30));

        AcaoIndisponivelException e = assertThrows(AcaoIndisponivelException.class, s::consultar);
        assertTrue(Set.of("saida do git grande demais", "git demorou demais").contains(e.getMessage()),
                e.getMessage());
    }

    @Test
    void codigoDeErroVira503SemDetalhe() throws Exception {
        List<String> cmd = programaJava("Falha", "System.err.println(\"C:/Users/segredo\"); System.exit(128);");
        GitStatusService s = new GitStatusService(pasta.toString(), cmd, Duration.ofSeconds(30));

        AcaoIndisponivelException e = assertThrows(AcaoIndisponivelException.class, s::consultar);
        assertEquals("repositorio indisponivel", e.getMessage());
    }

    // ---------- 3) Com o git de verdade ----------

    @Test
    void gitDeVerdade_limpoNovoEAlterado() throws Exception {
        precisaDoGit();
        git("init", "-q", "-b", "main");
        Files.writeString(pasta.resolve("a.txt"), "1");
        git("add", "a.txt");
        git("-c", "user.name=teste", "-c", "user.email=teste@exemplo.invalid", "commit", "-q", "-m", "inicio");

        GitStatusService s = new GitStatusService(pasta.toString());
        assertEquals(new Status("main", true, 0, 0, 0, 0), s.consultar());

        Files.writeString(pasta.resolve("a.txt"), "2");
        Files.writeString(pasta.resolve("novo.txt"), "x");
        assertEquals(new Status("main", false, 1, 1, 0, 0), s.consultar());
    }

    @Test
    void pastaQueNaoERepositorio_503() throws Exception {
        precisaDoGit();
        Path vazia = Files.createDirectory(pasta.resolve("nao-e-repo"));
        Files.createDirectory(vazia.resolve(".git")); // .git falso: o git recusa
        AcaoIndisponivelException e = assertThrows(AcaoIndisponivelException.class,
                () -> new GitStatusService(vazia.toString()).consultar());
        assertEquals("repositorio indisponivel", e.getMessage());
    }

    /**
     * Ataque: um .git/config com core.fsmonitor faz o "git status" RODAR um programa.
     * O Jarvis desliga isso no comando: o arquivo "invadido" não pode aparecer.
     */
    @Test
    void configMaliciosaDoRepositorioNaoRodaPrograma() throws Exception {
        precisaDoGit();
        git("init", "-q", "-b", "main");
        git("config", "core.fsmonitor", "echo invadido > invadido.txt; false");

        // Controle: o git "cru" roda o programa (prova que o ataque é real nesta máquina)
        git("status");
        Path marca = pasta.resolve("invadido.txt");
        Assumptions.assumeTrue(Files.exists(marca), "este git nao executou o fsmonitor; teste sem efeito");
        Files.delete(marca);

        new GitStatusService(pasta.toString()).consultar();
        assertFalse(Files.exists(marca), "o Jarvis deixou o .git/config rodar um programa");
    }

    // ---------- ajudantes ----------

    private static void precisaDoGit() {
        try {
            Process p = new ProcessBuilder("git", "--version").redirectErrorStream(true).start();
            p.getInputStream().readAllBytes();
            Assumptions.assumeTrue(p.waitFor(10, TimeUnit.SECONDS) && p.exitValue() == 0, "git nao instalado");
        } catch (Exception e) {
            Assumptions.abort("git nao instalado");
        }
    }

    private void git(String... args) throws Exception {
        List<String> cmd = new ArrayList<>(List.of("git"));
        cmd.addAll(List.of(args));
        Process p = new ProcessBuilder(cmd).directory(pasta.toFile()).redirectErrorStream(true).start();
        p.getInputStream().readAllBytes();
        assertTrue(p.waitFor(20, TimeUnit.SECONDS));
    }

    /** Cria um programinha Java (1 arquivo) e devolve o comando para rodá-lo no lugar do git. */
    private List<String> programaJava(String nome, String corpo) throws Exception {
        Path dir = Files.createDirectories(pasta.resolve("prog"));
        Path fonte = dir.resolve(nome + ".java");
        Files.writeString(fonte, "public class " + nome + " { public static void main(String[] a) throws Exception { "
                + corpo + " } }", StandardCharsets.UTF_8);
        String java = ProcessHandle.current().info().command().orElse("java");
        return List.of(java, fonte.toString());
    }
}
