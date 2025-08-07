package net.protsenko.fundy.app.dto;

import net.protsenko.fundy.app.exchange.ExchangeType;

import java.math.BigDecimal;
import java.util.Set;

public record ArbitrageFilter(
        Set<ExchangeType> exchanges,
        String zone,
        BigDecimal minFr,
        BigDecimal minPr
) {
}
