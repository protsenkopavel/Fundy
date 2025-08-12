package net.protsenko.fundy.app.exchange.impl.coinex;

public record CoinexFundingMeta(
        String market,
        long latestFundingTime,
        long nextFundingTime,
        String latestFundingRate,
        String nextFundingRate
) {
}