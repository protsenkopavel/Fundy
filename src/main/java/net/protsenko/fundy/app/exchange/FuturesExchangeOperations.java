package net.protsenko.fundy.app.exchange;

import net.protsenko.fundy.app.dto.rs.InstrumentData;
import net.protsenko.fundy.app.dto.rs.TickerData;

import java.util.List;

public interface FuturesExchangeOperations {
    List<InstrumentData> getFuturesInstruments();
    List<TickerData> getFuturesTickers(List<InstrumentData> instruments);
}