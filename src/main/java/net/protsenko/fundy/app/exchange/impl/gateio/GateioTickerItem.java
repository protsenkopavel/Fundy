package net.protsenko.fundy.app.exchange.impl.gateio;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public record GateioTickerItem(
        String contract,
        String last,
        String highestBid,
        String lowestAsk,
        String high24h,
        String low24h,
        String volume24h,
        String fundingRate,
        String fundingRateIndicative
) {
}
