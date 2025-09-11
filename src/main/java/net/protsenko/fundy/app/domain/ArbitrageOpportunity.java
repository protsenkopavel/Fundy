package net.protsenko.fundy.app.domain;

import net.protsenko.fundy.app.exchange.ExchangeType;

import java.math.BigDecimal;

public record ArbitrageOpportunity(
        ExchangeType longExchange,
        ExchangeType shortExchange,
        BigDecimal priceSpread,
        BigDecimal fundingSpread,
        ArbitrageType type
) {
}