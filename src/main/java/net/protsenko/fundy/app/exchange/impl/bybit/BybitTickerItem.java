package net.protsenko.fundy.app.exchange.impl.bybit;

import com.fasterxml.jackson.annotation.JsonProperty;

public record BybitTickerItem(
        String symbol,
        String lastPrice,
        String bid1Price,
        String ask1Price,
        String highPrice24h,
        String lowPrice24h,
        String volume24h,
        @JsonProperty("fundingRate")
        String fundingRate,
        @JsonProperty("nextFundingTime")
        String nextFundingTime
) {
}
