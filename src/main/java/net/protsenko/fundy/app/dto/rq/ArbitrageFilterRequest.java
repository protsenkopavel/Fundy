package net.protsenko.fundy.app.dto.rq;

import net.protsenko.fundy.app.exchange.ExchangeType;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.Set;

public record ArbitrageFilterRequest(
        Set<ExchangeType> exchanges,
        String timeZone,
        BigDecimal minFundingRate,
        BigDecimal minPerpetualPrice
) {
    public Set<ExchangeType> effectiveExchanges() {
        return (exchanges == null || exchanges.isEmpty())
                ? EnumSet.allOf(ExchangeType.class)
                : exchanges;
    }

    public ZoneId zone() {
        return timeZone == null
                ? ZoneId.systemDefault()
                : ZoneId.of(timeZone);
    }

    public BigDecimal minFr() {
        return minFundingRate == null
                ? BigDecimal.ZERO
                : minFundingRate;
    }

    public BigDecimal minPr() {
        return minPerpetualPrice == null
                ? BigDecimal.ZERO
                : minPerpetualPrice;
    }
}
