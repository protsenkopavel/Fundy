package net.protsenko.fundy.app.exchange;

import net.protsenko.fundy.app.dto.rs.FundingRateData;
import net.protsenko.fundy.app.dto.rs.InstrumentData;
import net.protsenko.fundy.app.dto.rs.TickerData;

import java.util.List;

public interface ExchangeClient {
    ExchangeType getExchangeType();

    Boolean isEnabled();

    List<InstrumentData> getAvailableInstruments();

    TickerData getTicker(InstrumentData instrument);

    List<TickerData> getTickers(List<InstrumentData> instruments);

    FundingRateData getFundingRate(InstrumentData instrument);

    List<FundingRateData> getAllFundingRates();
}
