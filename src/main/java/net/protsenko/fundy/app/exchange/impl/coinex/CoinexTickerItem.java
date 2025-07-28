package net.protsenko.fundy.app.exchange.impl.coinex;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CoinexTickerItem(
        String vol,
        String low,
        String open,
        String high,
        String last,
        String buy,
        int period,
        @JsonProperty("funding_time")
        long fundingTime,
        @JsonProperty("position_amount")
        String positionAmount,
        @JsonProperty("funding_rate_last")
        String fundingRateLast,
        @JsonProperty("funding_rate_next")
        String fundingRateNext,
        @JsonProperty("funding_rate_predict")
        String fundingRatePredict,
        String insurance,
        @JsonProperty("sign_price")
        String signPrice,
        @JsonProperty("index_price")
        String indexPrice,
        @JsonProperty("sell_total")
        String sellTotal,
        @JsonProperty("buy_total")
        String buyTotal,
        @JsonProperty("buy_amount")
        String buyAmount,
        String sell,
        @JsonProperty("sell_amount")
        String sellAmount
) {
}
