package net.protsenko.fundy.app.exchange.impl.kucoin;

public record KucoinContractItem(
        String symbol,
        String baseCurrency,
        String quoteCurrency,
        String status,
        String lastTradePrice,
        String markPrice,
        String indexPrice,
        String highPrice,
        String lowPrice,
        String openInterest,
        String turnoverOf24h,
        String volumeOf24h,
        String fundingFeeRate,
        String predictedFundingFeeRate,
        long nextFundingRateDateTime
) {
}
