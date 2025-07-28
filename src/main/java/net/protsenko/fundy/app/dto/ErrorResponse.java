package net.protsenko.fundy.app.dto;

public record ErrorResponse(
        String error,
        String message
) {
}
