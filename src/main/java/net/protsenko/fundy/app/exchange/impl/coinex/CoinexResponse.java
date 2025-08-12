package net.protsenko.fundy.app.exchange.impl.coinex;

public record CoinexResponse<T>(
        int code,
        T data,
        String message
) {
}
