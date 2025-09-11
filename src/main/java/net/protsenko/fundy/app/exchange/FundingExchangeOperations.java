package net.protsenko.fundy.app.exchange;

import net.protsenko.fundy.app.dto.rs.FundingRateData;
import net.protsenko.fundy.app.dto.rs.InstrumentData;

import java.util.List;

public interface FundingExchangeOperations {
    List<FundingRateData> getFundingRates(List<InstrumentData> instruments);
}