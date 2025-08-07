package net.protsenko.fundy.app.dto;

import java.math.BigDecimal;

public record FundingRateView(
        TradingInstrument instrument,
        BigDecimal ratePercent,
        long nextFundingTimeMs,
        String nextFundingTimeIso,
        String countdown
) {
}
