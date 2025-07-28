package net.protsenko.fundy.app.exchange.impl.bitget;

public record BitgetResponse<T>(
        String code,
        String msg,
        Long requestTime,
        T data
) {
}