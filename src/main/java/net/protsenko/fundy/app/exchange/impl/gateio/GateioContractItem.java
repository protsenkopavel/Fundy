package net.protsenko.fundy.app.exchange.impl.gateio;

import com.fasterxml.jackson.annotation.JsonProperty;

public record GateioContractItem(
        @JsonProperty("name")
        String name,
        @JsonProperty("status")
        String status,
        @JsonProperty("asset")
        String asset,
        @JsonProperty("currency")
        String currency,
        @JsonProperty("funding_rate")
        String fundingRate,
        @JsonProperty("funding_rate_indicative")
        String fundingRateIndicative,
        @JsonProperty("funding_next_apply")
        long fundingNextApply,
        @JsonProperty("funding_interval")
        int fundingInterval,
        @JsonProperty("mark_price")
        String markPrice,
        @JsonProperty("last_price")
        String lastPrice
) {
}
