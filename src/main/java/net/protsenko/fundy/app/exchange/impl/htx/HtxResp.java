package net.protsenko.fundy.app.exchange.impl.htx;

public record HtxResp<T>(
        String status,
        T data,
        Long ts
) {
}
