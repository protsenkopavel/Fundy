package net.protsenko.fundy.app.dto.rq;

import net.protsenko.fundy.app.exchange.ExchangeType;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.Set;

public record FuturesArbitrageRequest(
        Set<ExchangeType> exchanges,
        BigDecimal minFundingRate,
        BigDecimal maxFundingRate,
        BigDecimal minPerpetualPrice,
        BigDecimal maxPerpetualPrice,
        Boolean sameAccrualTime,
        BigDecimal minVolume
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

    public BigDecimal minPr() {
        return minPerpetualPrice == null
                ? BigDecimal.ZERO
                : minPerpetualPrice;
    }

    public BigDecimal maxFr() {
        return maxFundingRate == null
                ? new BigDecimal("100")
                : maxFundingRate;
    }

    public BigDecimal maxPr() {
        return maxPerpetualPrice == null
                ? new BigDecimal("100")
                : maxPerpetualPrice;
    }

    public BigDecimal effectiveMinVolume() {
        return minVolume == null
                ? BigDecimal.valueOf(10000.0)
                : minVolume;
    }

}
