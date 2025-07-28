package net.protsenko.fundy.app.exchange.impl.kucoin;

import com.fasterxml.jackson.annotation.JsonProperty;

public record KucoinContractItem(
        String symbol,
        String baseCurrency,
        String quoteCurrency,
        String status,
        @JsonProperty("lastTradePrice") String lastTradePrice,
        @JsonProperty("markPrice") String markPrice,
        @JsonProperty("indexPrice") String indexPrice,
        @JsonProperty("highPrice") String highPrice,
        @JsonProperty("lowPrice") String lowPrice,
        String openInterest,
        @JsonProperty("turnoverOf24h") String turnoverOf24h,
        @JsonProperty("volumeOf24h") String volumeOf24h,
        @JsonProperty("fundingFeeRate") String fundingFeeRate,
        @JsonProperty("predictedFundingFeeRate") String predictedFundingFeeRate,
        @JsonProperty("nextFundingRateDateTime") long nextFundingRateDateTime
) {
}
