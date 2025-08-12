package net.protsenko.fundy.app.dto.rq;

import net.protsenko.fundy.app.exchange.ExchangeType;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.Set;

public record FundingFilterRequest(
        Set<ExchangeType> exchanges,
        BigDecimal minFundingRate
) {
    public Set<ExchangeType> effectiveExchanges() {
        return (exchanges == null || exchanges.isEmpty())
                ? EnumSet.allOf(ExchangeType.class)
                : exchanges;
    }

    public BigDecimal minFr() {
        return minFundingRate == null
                ? BigDecimal.ZERO
                : minFundingRate;
    }
}