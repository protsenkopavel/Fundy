package net.protsenko.fundy.app.exchange.impl.bingx;

public record BingxTickerItem(
        String symbol,
        String lastPrice,
        String bestBid,
        String bestAsk,
        String high24h,
        String low24h,
        String volume24h
) {
}
