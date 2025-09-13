package net.protsenko.fundy.app.exchange.impl.bingx;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record BingxSpotTickerItem(
        String symbol,
        String openPrice,
        String highPrice,
        String lowPrice,
        String lastPrice,
        String priceChange,
        String priceChangePercent,
        String volume,
        String quoteVolume,
        String openTime,
        String closeTime,
        String askPrice,
        String askQty,
        String bidPrice,
        String bidQty
) {
}