package net.protsenko.fundy.app.exchange.impl.bingx;

public record BingxResponse<T>(
        int code,
        String msg,
        T data
) {
}
