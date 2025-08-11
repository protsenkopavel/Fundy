package net.protsenko.fundy.app.dto.rq;

import jakarta.validation.Valid;
import net.protsenko.fundy.app.dto.InstrumentPair;
import net.protsenko.fundy.app.exchange.ExchangeType;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public record TickersRequest(
        Set<ExchangeType> exchanges,
        List<@Valid InstrumentPair> pairs
) {
    public Set<ExchangeType> effectiveExchanges() {
        return (exchanges == null || exchanges.isEmpty())
                ? EnumSet.allOf(ExchangeType.class)
                : exchanges;
    }
    public boolean hasPairs() {
        return pairs != null && !pairs.isEmpty();
    }
}