package net.protsenko.fundy.app.exchange.impl.bitget;


public record BitgetFundingMeta(
        String symbol,
        String fundingRate,
        String fundingRateInterval,
        String nextUpdate,
        String minFundingRate,
        String maxFundingRate
) {
}
