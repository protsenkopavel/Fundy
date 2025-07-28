package net.protsenko.fundy.app.exchange.impl.mexc;

public record MexcTickerResponse(
        int code,
        String msg,
        MexcTickerItem data
) {
}