package net.protsenko.fundy.app.exchange.impl.mexc;

public record MexcFundingItem(
        String symbol,
        String fundingRate,
        String nextSettleTime
) {
}
