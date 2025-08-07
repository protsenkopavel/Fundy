package net.protsenko.fundy.app.exchange;

import net.protsenko.fundy.app.dto.FundingRateData;
import net.protsenko.fundy.app.dto.TickerData;
import net.protsenko.fundy.app.dto.TradingInstrument;

import java.util.List;

public interface ExchangeClient {
    ExchangeType getExchangeType();

    boolean isEnabled();

    List<TradingInstrument> getAvailableInstruments();

    TickerData getTicker(TradingInstrument instrument);

    List<TickerData> getTickers(List<TradingInstrument> instruments);

    FundingRateData getFundingRate(TradingInstrument instrument);

    List<FundingRateData> getAllFundingRates();
}
