package net.protsenko.fundy.app.exchange.impl.okx;

import com.fasterxml.jackson.annotation.JsonProperty;

public record OkxTickerItem(
        String instId,
        String instType,
        String last,
        String askPx,
        String bidPx,
        String high24h,
        String low24h,
        @JsonProperty("vol24h")
        String vol24h,
        @JsonProperty("ts")
        String ts
) {
}
