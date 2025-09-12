package net.protsenko.fundy.app.dto.rs;

import net.protsenko.fundy.app.exchange.ExchangeType;

import java.math.BigDecimal;

public record SpotArbitrageData(
        String coin,
        ExchangeType buyExchange,
        boolean withdrawalEnabled,
        ExchangeType sellExchange,
        boolean depositEnabled,
        BigDecimal priceSpread,
        BigDecimal buyVolume24h,
        BigDecimal sellVolume24h
) {
}