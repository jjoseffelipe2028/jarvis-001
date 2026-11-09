package io.github.jjoseffelipe2028.bracos.erros;

import java.io.UncheckedIOException;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Erros viram mensagem curta em JSON, sem stack trace e sem caminho de arquivo. 🔒
 */
@RestControllerAdvice
public class ErrosHandler {

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class,
            EntradaInvalidaException.class, org.springframework.beans.TypeMismatchException.class,
            jakarta.validation.ConstraintViolationException.class})
    public ResponseEntity<Map<String, String>> entradaInvalida(Exception e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("erro", "entrada invalida"));
    }

    /** Ação desligada por configuração (ex.: pasta não configurada) → 503. */
    @ExceptionHandler(AcaoIndisponivelException.class)
    public ResponseEntity<Map<String, String>> indisponivel(AcaoIndisponivelException e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("erro", e.getMessage()));
    }

    /** Falha de disco: a mensagem real (com caminho) NÃO vai para o cliente. */
    @ExceptionHandler(UncheckedIOException.class)
    public ResponseEntity<Map<String, String>> falhaAoGravar(UncheckedIOException e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("erro", "falha ao gravar"));
    }

    /** Trava interna disparou (ex.: caminho fora da pasta) → 500 genérico, sem detalhe. */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> erroInterno(IllegalStateException e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("erro", "erro interno"));
    }

    /**
     * Ticket 008: rede de segurança para QUALQUER outro erro. 🔒
     * - Erros do próprio Spring que já têm status (404 rota não existe, 405 método errado...)
     *   mantêm o status, com uma mensagem fixa.
     * - Todo o resto vira 500 "erro interno": nada de stack trace, caminho ou mensagem de dentro.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> qualquerOutro(Exception e) {
        if (e instanceof ErrorResponse comStatus) {
            return ResponseEntity.status(comStatus.getStatusCode()).body(Map.of("erro", "pedido nao suportado"));
        }
        log.error("acao=erro_inesperado tipo={}", e.getClass().getSimpleName());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("erro", "erro interno"));
    }

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ErrosHandler.class);
}
