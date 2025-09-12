package net.protsenko.fundy.app.dto.rq;

import jakarta.validation.constraints.DecimalMin;
import net.protsenko.fundy.app.exchange.ExchangeType;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.Set;

public record SpotArbitrageRequest(
        Set<ExchangeType> exchanges,
        @DecimalMin(value = "0.0", inclusive = false)
        BigDecimal minSpread
) {
    public Set<ExchangeType> effectiveExchanges() {
        return (exchanges == null || exchanges.isEmpty())
                ? EnumSet.allOf(ExchangeType.class)
                : exchanges;
    }

    public BigDecimal effectiveMinSpread() {
        return minSpread != null ? minSpread : BigDecimal.valueOf(0.1);
    }
}