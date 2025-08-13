package net.protsenko.fundy.app.exchange.impl.coinex;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public record CoinexContractItem(
        String name,
        int type,
        String stock,
        String money,
        boolean available,
        Funding funding
) {
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Funding(
            int interval,
            String max,
            String min
    ) {
    }
}
