package net.protsenko.fundy.app.exchange;

import net.protsenko.fundy.app.dto.rs.FundingRateData;
import net.protsenko.fundy.app.dto.rs.InstrumentData;
import net.protsenko.fundy.app.dto.rs.TickerData;

import java.util.List;

public interface ExchangeClient {
    List<InstrumentData> getFuturesInstruments();

    List<TickerData> getFuturesTickers(List<InstrumentData> instruments);

    List<FundingRateData> getFundingRates(List<InstrumentData> instruments);

    List<InstrumentData> getSpotInstruments();

    List<TickerData> getSpotTickers(List<InstrumentData> instruments);

    ExchangeType getExchangeType();

    Boolean isEnabled();
}
