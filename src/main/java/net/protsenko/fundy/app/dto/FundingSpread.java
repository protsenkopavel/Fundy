package net.protsenko.fundy.app.dto;

import net.protsenko.fundy.app.exchange.ExchangeType;

import java.math.BigDecimal;

public record FundingSpread(
        ExchangeType ex1,
        ExchangeType ex2,
        BigDecimal spread
) {
}
