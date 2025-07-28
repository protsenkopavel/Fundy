package net.protsenko.fundy.app.exchange.impl.gateio;

import com.fasterxml.jackson.annotation.JsonProperty;

public record GateioTickerItem(
        @JsonProperty("contract")
        String contract,
        @JsonProperty("last")
        String last,
        @JsonProperty("highest_bid")
        String highestBid,
        @JsonProperty("lowest_ask")
        String lowestAsk,
        @JsonProperty("high_24h")
        String high24h,
        @JsonProperty("low_24h")
        String low24h,
        @JsonProperty("volume_24h")
        String volume24h,
        @JsonProperty("funding_rate")
        String fundingRate,
        @JsonProperty("funding_rate_indicative")
        String fundingRateIndicative
) {
}
