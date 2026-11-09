package io.github.jjoseffelipe2028.bracos.acoes;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;

import io.github.jjoseffelipe2028.bracos.erros.AcaoIndisponivelException;

/**
 * Regras da "pasta permitida", usadas por toda ação que mexe em arquivo. 🔒
 * - A pasta vem só da configuração (.env), nunca do pedido.
 * - Todo arquivo tem que ficar DENTRO dela (anti Path Traversal).
 */
public final class PastaSegura {

    private PastaSegura() {
    }

    /** Pasta vazia ou que não existe → 503 (falha fechada: na dúvida, nega). */
    public static Path base(String pastaConfigurada) {
        if (pastaConfigurada == null || pastaConfigurada.isBlank()) {
            throw new AcaoIndisponivelException("pasta nao configurada");
        }
        try {
            Path base = Path.of(pastaConfigurada).toRealPath();
            if (!Files.isDirectory(base)) {
                throw new AcaoIndisponivelException("pasta indisponivel");
            }
            return base;
        } catch (IOException | InvalidPathException e) {
            throw new AcaoIndisponivelException("pasta indisponivel");
        }
    }

    /** Garante que o arquivo fica DENTRO da pasta permitida. Fora → erro. */
    public static Path dentroDaPasta(Path base, String nomeArquivo) {
        Path alvo = base.resolve(nomeArquivo).normalize();
        if (!alvo.startsWith(base) || alvo.equals(base)) {
            throw new IllegalStateException("caminho fora da pasta permitida");
        }
        return alvo;
    }
}
