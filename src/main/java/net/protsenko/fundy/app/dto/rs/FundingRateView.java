package net.protsenko.fundy.app.dto.rs;

import java.math.BigDecimal;

public record FundingRateView(
        String symbol,
        String base,
        String quote,
        String exchange,
        BigDecimal fundingRate,
        long nextFundingTs
) {
    public static FundingRateView of(FundingRateData fr) {
        return new FundingRateView(
                fr.symbol(),
                fr.instrument().baseAsset(),
                fr.instrument().quoteAsset(),
                fr.exchange().name(),
                fr.fundingRate(),
                fr.nextFundingTs()
        );
    }
}
