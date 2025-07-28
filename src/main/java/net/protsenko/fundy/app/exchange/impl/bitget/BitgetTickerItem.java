package net.protsenko.fundy.app.exchange.impl.bitget;

public record BitgetTickerItem(
        String symbol,
        String last,
        String bestAsk,
        String bestBid,
        String high24h,
        String low24h,
        String baseVolume,
        String quoteVolume,
        String usdtVolume,
        String fundingRate,
        String timestamp
) {
}
