package net.protsenko.fundy.app.exchange;

import net.protsenko.fundy.app.dto.TradingInstrument;

public final class InstrumentSymbolConverter {
    private InstrumentSymbolConverter() {
    }

    public static String toBybitLinearSymbol(TradingInstrument instrument) {
        return instrument.baseAsset() + instrument.quoteAsset();
    }
}