package net.protsenko.fundy.app.exchange.impl.gateio;

public record GateioTickerItem(
        String contract,
        String last,
        String highestBid,
        String lowestAsk,
        String high24h,
        String low24h,
        String volume24h,
        String fundingRate,
        String fundingRateIndicative
) {
}
