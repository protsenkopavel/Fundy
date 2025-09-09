package net.protsenko.fundy.app.exchange.impl.coinex;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public record CoinexSpotTickerItem(
        String market,
        String last,
        String buy,
        String sell,
        String high,
        String low,
        String vol,
        String amount,
        String period
) {
}