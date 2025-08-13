package net.protsenko.fundy.app.dto.rs;


import com.fasterxml.jackson.annotation.JsonProperty;
import net.protsenko.fundy.app.exchange.ExchangeType;

import java.util.Map;
import java.util.Set;

public record UniverseEntry(
        String base,
        String quote,
        Map<ExchangeType, String> nativeSymbols
) {
    @JsonProperty("token")
    public String token() {
        return (base + "/" + quote).toUpperCase();
    }

    @JsonProperty("exchanges")
    public Set<ExchangeType> exchanges() {
        return nativeSymbols.keySet();
    }

    @JsonProperty("coverage")
    public int coverage() {
        return nativeSymbols.size();
    }
}
