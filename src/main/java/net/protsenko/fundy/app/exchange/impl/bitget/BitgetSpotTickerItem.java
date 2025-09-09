package net.protsenko.fundy.app.exchange.impl.bitget;

public record BitgetSpotTickerItem(
        String symbol,
        String high24h,
        String low24h,
        String close,
        String quoteVol,
        String baseVol,
        String usdtVol,
        String ts,
        String bidPr,
        String askPr,
        String bidSz,
        String askSz,
        String openUtc0,
        String changeUtc0,
        String changeRateUtc0
) {
}