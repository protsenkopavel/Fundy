package net.protsenko.fundy.app.exception;

public class ExchangeException extends RuntimeException {
    public ExchangeException(String message) {
        super(message);
    }

    public ExchangeException(String message, Throwable cause) {
        super(message, cause);
    }
}
