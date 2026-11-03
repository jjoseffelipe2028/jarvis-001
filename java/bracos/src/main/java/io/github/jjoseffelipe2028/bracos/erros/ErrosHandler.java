package io.github.jjoseffelipe2028.bracos.erros;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Erros viram mensagem curta em JSON, sem stack trace. 🔒
 */
@RestControllerAdvice
public class ErrosHandler {

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<Map<String, String>> entradaInvalida(Exception e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("erro", "entrada invalida"));
    }
}
