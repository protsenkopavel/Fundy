package net.protsenko.fundy.app.dto;

import net.protsenko.fundy.app.exchange.ExchangeType;

import java.math.BigDecimal;

public record BucketEntry(
        String symbol,
        ExchangeType ex,
        BigDecimal price,
        BigDecimal funding,
        long nextFundingTs
) {
}
