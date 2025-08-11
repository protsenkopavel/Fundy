package net.protsenko.fundy.app.dto.rs;

public record ErrorResponse(
        String error,
        String message
) {
}
