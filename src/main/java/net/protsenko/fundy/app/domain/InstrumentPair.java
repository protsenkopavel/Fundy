package net.protsenko.fundy.app.domain;

import jakarta.validation.constraints.NotBlank;

public record InstrumentPair(
        @NotBlank String base,
        @NotBlank String quote
) {}
