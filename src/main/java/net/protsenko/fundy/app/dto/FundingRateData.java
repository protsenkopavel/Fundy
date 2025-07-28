package net.protsenko.fundy.app.dto;

import java.math.BigDecimal;

public record FundingRateData(
        TradingInstrument instrument,
        BigDecimal fundingRate,
        long nextFundingTimeMs
) {
}
