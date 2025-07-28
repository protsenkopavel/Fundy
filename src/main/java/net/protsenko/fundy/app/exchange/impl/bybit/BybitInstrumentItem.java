package net.protsenko.fundy.app.exchange.impl.bybit;


public record BybitInstrumentItem(
        String symbol,
        String baseCoin,
        String quoteCoin,
        String status
) {
}
