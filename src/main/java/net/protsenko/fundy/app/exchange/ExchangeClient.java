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

    FundingRateData getFundingRate(TradingInstrument instrument);

    default List<FundingRateData> getAllFundingRates() {
        throw new UnsupportedOperationException("Not implemented");
    }

    default List<TickerData> getTickers(List<TradingInstrument> instruments) {
        return instruments.stream().map(this::getTicker).toList();
    }
}
