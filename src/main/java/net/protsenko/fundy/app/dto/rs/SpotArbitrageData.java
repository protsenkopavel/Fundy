package net.protsenko.fundy.app.dto.rs;

import net.protsenko.fundy.app.exchange.ExchangeType;

import java.math.BigDecimal;
import java.util.Map;

public record SpotArbitrageData(
        String coin,
        String pair,
        ExchangeType buyExchange,
        DepositWithdrawStatus withdrawalStatus,
        ExchangeType sellExchange,
        DepositWithdrawStatus depositStatus,
        BigDecimal priceSpread,
        BigDecimal buyPrice,
        BigDecimal sellPrice,
        BigDecimal buyVolume24h,
        BigDecimal sellVolume24h,
        Map<ExchangeType, String> links
) {
}