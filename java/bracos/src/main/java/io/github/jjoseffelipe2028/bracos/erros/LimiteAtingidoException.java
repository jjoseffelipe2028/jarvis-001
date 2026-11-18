package io.github.jjoseffelipe2028.bracos.erros;

/**
 * Limite de uso atingido (ex.: pedidos demais no dia) → 429. 🔒
 * A mensagem vai para o cliente: nunca coloque caminho, token ou dado do usuário nela.
 */
public class LimiteAtingidoException extends RuntimeException {

    public LimiteAtingidoException(String mensagem) {
        super(mensagem);
    }
}
