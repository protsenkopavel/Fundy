package net.protsenko.fundy.app.exchange.impl.gateio;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public record GateioSpotTickerItem(
        String currencyPair,
        String last,
        String lowestAsk,
        String highestBid,
        String changePercentage,
        String baseVolume,
        String quoteVolume,
        String high24h,
        String low24h
) {
}