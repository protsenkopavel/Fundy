package net.protsenko.fundy.app.exchange.impl.mexc;

public record MexcTickerItem(
        String symbol,
        String lastPrice,
        String bid1Price,
        String ask1Price,
        String high24Price,
        String low24Price,
        String volume24
) {
}