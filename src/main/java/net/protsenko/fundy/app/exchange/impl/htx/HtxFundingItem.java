package net.protsenko.fundy.app.exchange.impl.htx;

import com.fasterxml.jackson.annotation.JsonProperty;

public record HtxFundingItem(
        @JsonProperty("estimated_rate")
        String estimatedRate,
        @JsonProperty("funding_rate")
        String fundingRate,
        @JsonProperty("contract_code")
        String contractCode,
        String symbol,
        @JsonProperty("fee_asset")
        String feeAsset,
        @JsonProperty("funding_time")
        String fundingTime,
        @JsonProperty("next_funding_time")
        String nextFundingTime
) {
}
