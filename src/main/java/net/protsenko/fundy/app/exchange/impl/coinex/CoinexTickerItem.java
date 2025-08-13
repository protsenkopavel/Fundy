package net.protsenko.fundy.app.exchange.impl.coinex;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public record CoinexTickerItem(
        String vol,
        String low,
        String open,
        String high,
        String last,
        String buy,
        int period,
        long fundingTime,
        String positionAmount,
        String fundingRateLast,
        String fundingRateNext,
        String fundingRatePredict,
        String insurance,
        String signPrice,
        String indexPrice,
        String sellTotal,
        String buyTotal,
        String buyAmount,
        String sell,
        String sellAmount
) {
}
