package net.protsenko.fundy.app.exchange.impl.mexc;

import com.fasterxml.jackson.annotation.JsonProperty;

public record MexcFundingItem(
        String symbol,
        String fundingRate,
        @JsonProperty("nextSettleTime")
        String fundingTime
) {
}
