package net.protsenko.fundy.app.dto;

import java.math.BigDecimal;

public record FundingRateViewEx(
        String exchange,
        String symbol,
        BigDecimal rate,
        String nextFundingTime
) {
}
