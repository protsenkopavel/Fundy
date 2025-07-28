package net.protsenko.fundy.app.dto;

import jakarta.validation.constraints.NotBlank;

public record TradingInstrumentRequest(@NotBlank String base, @NotBlank String quote) {
}
