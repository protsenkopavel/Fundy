package net.protsenko.fundy.app.exchange;

import net.protsenko.fundy.app.dto.TradingInstrument;

public interface InstrumentSymbolConverter {
    String toExchangeSymbol(TradingInstrument instrument);
    TradingInstrument fromExchangeSymbol(String symbol);
}