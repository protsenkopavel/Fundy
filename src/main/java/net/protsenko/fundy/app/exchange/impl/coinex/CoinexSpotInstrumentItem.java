package net.protsenko.fundy.app.exchange.impl.coinex;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public record CoinexSpotInstrumentItem(
        String name,
        String stock,
        String money,
        boolean isMargin,
        boolean isMining,
        boolean isShares,
        int tradingStatus,
        String makerFeeRate,
        String takerFeeRate,
        String minAmount,
        String maxAmount,
        String minMoney,
        String maxMoney
) {
}