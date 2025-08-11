package net.protsenko.fundy.app.dto.rq;

import net.protsenko.fundy.app.dto.InstrumentPair;
import net.protsenko.fundy.app.exchange.ExchangeType;

import java.util.EnumSet;
import java.util.Set;

public record TickerRequest(
        Set<ExchangeType> exchanges,
        InstrumentPair pair
) {
    public Set<ExchangeType> effectiveExchanges() {
        return (exchanges == null || exchanges.isEmpty())
                ? EnumSet.allOf(ExchangeType.class)
                : exchanges;
    }
}