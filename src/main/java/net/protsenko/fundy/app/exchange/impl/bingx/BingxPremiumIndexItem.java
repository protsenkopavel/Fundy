package net.protsenko.fundy.app.exchange.impl.bingx;

public record BingxPremiumIndexItem(
        String symbol,
        String markPrice,
        String indexPrice,
        String lastFundingRate,
        long nextFundingTime
) {
}
