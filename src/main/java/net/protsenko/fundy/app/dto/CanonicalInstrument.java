package net.protsenko.fundy.app.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;

public record CanonicalInstrument(String base, String quote) {
    public CanonicalInstrument {
        base = base == null ? "" : base.toUpperCase();
        quote = quote == null ? "" : quote.toUpperCase();
    }

    @JsonIgnore
    public String canonicalKey() {
        return base + "/" + quote;
    }
}