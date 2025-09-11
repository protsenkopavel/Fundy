package net.protsenko.fundy.app.domain;

import net.protsenko.fundy.app.exchange.ExchangeType;

import java.math.BigDecimal;

public record ArbitrageCandidate(
        ExchangeType longEx,
        ExchangeType shortEx,
        BigDecimal priceSpread,
        BigDecimal fundingSpread,
        BigDecimal score,
        ArbitrageType type
) {
}
