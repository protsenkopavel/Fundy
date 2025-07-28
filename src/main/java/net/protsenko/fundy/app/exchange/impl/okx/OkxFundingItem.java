package net.protsenko.fundy.app.exchange.impl.okx;

public record OkxFundingItem(
        String instId,
        String instType,
        String fundingRate,
        String nextFundingRate,
        String fundingTime,
        String nextFundingTime
) {
}