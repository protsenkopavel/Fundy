package net.protsenko.fundy.app.exchange.impl.kucoin;

public record KucoinTickerData(
        long sequence,
        String symbol,
        String side,
        String size,
        String tradeId,
        String price,
        String bestBidPrice,
        String bestBidSize,
        String bestAskPrice,
        String bestAskSize,
        long ts
) {
}
