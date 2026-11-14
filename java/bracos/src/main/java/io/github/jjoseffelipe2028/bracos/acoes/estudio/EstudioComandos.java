package io.github.jjoseffelipe2028.bracos.acoes.estudio;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Os comandos FIXOS dos agentes do Estúdio (Tickets 047 e 048). O texto do José nunca entra aqui: ele vai num arquivo. 🔒
 * - 001 (Claude): sem terminal, sem internet, sem MCP, sem perguntar ({@code dontAsk} = o que não está liberado é negado).
 *   Regras de "deny" ({@code .env}, pasta {@code .claude}) num arquivo FORA da pasta onde a 001 trabalha.
 * - 002 (Antigravity): modo {@code plan} (só analisa).
 */
final class EstudioComandos {

    private static Path regras;

    private EstudioComandos() {
    }

    static List<String> claude(String bin, String prompt, boolean escreve) throws IOException {
        return List.of(bin, "-p", prompt,
                "--allowedTools", escreve ? "Read,Write,Edit,Glob,Grep,Skill" : "Read,Glob,Grep,Skill",
                "--disallowedTools", "Bash,PowerShell,WebFetch,WebSearch",
                "--permission-mode", "dontAsk", "--strict-mcp-config", "--settings", arquivoDeRegras().toString());
    }

    static List<String> agy(String bin, String prompt) {
        return List.of(bin, "--mode", "plan", "--print-timeout", "330s", "-p=" + prompt);
    }

    /** No Windows, aspas dentro de argumento se perdem: por isso as regras vão num arquivo. */
    private static synchronized Path arquivoDeRegras() throws IOException {
        if (regras == null || !Files.isRegularFile(regras)) {
            Path arquivo = Files.createTempFile("jarvis-estudio-", ".json");
            Files.writeString(arquivo, EstudioService.DENY, StandardCharsets.UTF_8);
            arquivo.toFile().deleteOnExit();
            regras = arquivo;
        }
        return regras;
    }
}
