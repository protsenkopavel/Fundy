package net.protsenko.fundy.app.exchange.impl.coinex;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CoinexResponse<T>(
        @JsonProperty("code")
        int code,
        @JsonProperty("data")
        T data,
        @JsonProperty("message")
        String message
) {}
