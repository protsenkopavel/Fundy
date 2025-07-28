package net.protsenko.fundy.app.exchange.impl.bybit;

public record BybitTickerItem(
        String symbol,
        String lastPrice,
        String bid1Price,
        String ask1Price,
        String highPrice24h,
        String lowPrice24h,
        String volume24h
) {}
