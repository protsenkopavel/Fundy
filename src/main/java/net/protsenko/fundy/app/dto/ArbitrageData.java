package net.protsenko.fundy.app.dto;

import net.protsenko.fundy.app.exchange.ExchangeType;

import java.math.BigDecimal;
import java.util.Map;

public record ArbitrageData(
        String token,
        Map<ExchangeType, BigDecimal> prices,
        Map<ExchangeType, BigDecimal> fundingRates,
        Map<ExchangeType, Long> nextFundingTs,
        BigDecimal priceSpread,
        BigDecimal fundingSpread,
        Decision decision
) {
    public record Decision(
            ExchangeType longEx, ExchangeType shortEx
    ) {
    }
}
