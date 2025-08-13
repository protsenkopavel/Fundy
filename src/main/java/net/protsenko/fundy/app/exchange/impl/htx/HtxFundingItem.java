package net.protsenko.fundy.app.exchange.impl.htx;

public record HtxFundingItem(
        String estimatedRate,
        String fundingRate,
        String contractCode,
        String symbol,
        String feeAsset,
        String fundingTime,
        String nextFundingTime
) {
}
