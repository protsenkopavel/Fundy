package net.protsenko.fundy.app.dto.rq;

import net.protsenko.fundy.app.exchange.ExchangeType;

import java.util.EnumSet;
import java.util.Set;

public record InstrumentsRequest(
        Set<ExchangeType> exchanges
) {
    public Set<ExchangeType> effectiveExchanges() {
        return (exchanges == null || exchanges.isEmpty())
                ? EnumSet.allOf(ExchangeType.class)
                : exchanges;
    }
}
