package net.protsenko.fundy.app.exchange.impl.kucoin;

public record KucoinSpotInstrumentItem(
        String symbol,
        String baseCurrency,
        String quoteCurrency
) {
}