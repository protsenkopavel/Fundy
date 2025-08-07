package net.protsenko.fundy.app.dto;

import net.protsenko.fundy.app.exchange.ExchangeType;

import java.math.BigDecimal;
import java.util.Map;

public record ArbitrageData(
        String baseAsset,
        Map<ExchangeType, BigDecimal> prices,
        Map<ExchangeType, BigDecimal> fundingRates,
        BigDecimal priceSpread,
        BigDecimal fundingSpread,
        ArbitrageDecision decision
) {
    public record ArbitrageDecision(
            ExchangeType longEx, ExchangeType shortEx
    ) {
    }
}
