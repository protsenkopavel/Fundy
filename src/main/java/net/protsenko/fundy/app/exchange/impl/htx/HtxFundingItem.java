package net.protsenko.fundy.app.exchange.impl.htx;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public record HtxFundingItem(
        String estimatedRate,
        String fundingRate,
        String contractCode,
        String symbol,
        String feeAsset,
        String fundingTime,
        String nextFundingTime
) {
}
