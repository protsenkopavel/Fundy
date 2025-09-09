package net.protsenko.fundy.app.exchange.impl.bingx;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public record BingxSpotTickerItem(
        String symbol,
        String lastPrice,
        String bidPrice,
        String askPrice,
        String highPrice,
        String lowPrice,
        String volume,
        String amount,
        String openPrice,
        String closePrice,
        String count
) {
}