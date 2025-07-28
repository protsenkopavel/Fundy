package net.protsenko.fundy.app.controller;

import lombok.extern.slf4j.Slf4j;
import net.protsenko.fundy.app.dto.ErrorResponse;
import net.protsenko.fundy.app.exception.ExchangeException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(ExchangeException.class)
    public ResponseEntity<ErrorResponse> handleExchange(ExchangeException ex) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(new ErrorResponse("exchange_error", ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleOther(Exception ex) {
        log.error("Unhandled error", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("internal_error", ex.getMessage()));
    }
}