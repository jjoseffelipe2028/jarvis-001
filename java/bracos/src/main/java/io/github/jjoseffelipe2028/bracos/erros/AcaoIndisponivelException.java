package io.github.jjoseffelipe2028.bracos.erros;

/**
 * A ação existe, mas não pode rodar agora (ex.: pasta não configurada) → 503.
 * A mensagem vai para o cliente: nunca coloque caminho, token ou dado do usuário nela. 🔒
 */
public class AcaoIndisponivelException extends RuntimeException {

    public AcaoIndisponivelException(String mensagem) {
        super(mensagem);
    }
}
