package net.protsenko.fundy.app.exchange.impl.bingx;

import com.fasterxml.jackson.annotation.JsonProperty;

public record BingxTickerItem(
        String symbol,
        String lastPrice,
        String bestBid,
        String bestAsk,
        String high24h,
        String low24h,
        @JsonProperty("volume")
        String volume24h
) {
}
