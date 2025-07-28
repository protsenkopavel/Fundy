package net.protsenko.fundy.app.dto;

public record TradingInstrument(
        String baseAsset,
        String quoteAsset,
        InstrumentType type,
        String nativeSymbol
) {
}

