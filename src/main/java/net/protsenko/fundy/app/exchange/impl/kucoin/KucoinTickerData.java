package net.protsenko.fundy.app.exchange.impl.kucoin;

import com.fasterxml.jackson.annotation.JsonProperty;

public record KucoinTickerData(
        long sequence,
        String symbol,
        String side,
        String size,
        String tradeId,
        @JsonProperty("last") String price,
        @JsonProperty("buy") String bestBidPrice,
        @JsonProperty("sell") String bestAskPrice,
        String bestBidSize,
        String bestAskSize,
        long ts,
        @JsonProperty("high") String high,
        @JsonProperty("low") String low,
        @JsonProperty("vol") String vol
) {
}
