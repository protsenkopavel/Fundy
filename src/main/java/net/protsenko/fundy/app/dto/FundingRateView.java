package net.protsenko.fundy.app.dto;

import java.math.BigDecimal;

public record FundingRateView(
        TradingInstrument instrument,
        BigDecimal ratePercent,      // -0.321319
        long nextFundingTimeMs,  // 1753747200000
        String nextFundingTimeIso, // "2025-07-29T02:00:00+02:00"
        String countdown           // "05:18:53"
) {
}
