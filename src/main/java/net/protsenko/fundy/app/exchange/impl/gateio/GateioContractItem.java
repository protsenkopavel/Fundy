package net.protsenko.fundy.app.exchange.impl.gateio;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public record GateioContractItem(
        String name,
        String status,
        String asset,
        String currency,
        String fundingRate,
        String fundingRateIndicative,
        long fundingNextApply,
        int fundingInterval,
        String markPrice,
        String lastPrice
) {
}
