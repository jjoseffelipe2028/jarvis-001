package io.github.jjoseffelipe2028.bracos.erros;

/**
 * Entrada que passou no @Valid mas não serve (ex.: texto só com caracteres de controle) → 400.
 */
public class EntradaInvalidaException extends RuntimeException {

    public EntradaInvalidaException(String mensagem) {
        super(mensagem);
    }
}
