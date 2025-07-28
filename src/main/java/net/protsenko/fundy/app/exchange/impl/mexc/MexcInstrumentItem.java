package net.protsenko.fundy.app.exchange.impl.mexc;

public record MexcInstrumentItem(
        String symbol,
        String baseCoin,
        String quoteCoin,
        int state
) {
}
