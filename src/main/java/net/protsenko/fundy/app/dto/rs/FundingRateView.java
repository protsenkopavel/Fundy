package net.protsenko.fundy.app.dto.rs;

import net.protsenko.fundy.app.utils.ExchangeLinkResolver;

import java.math.BigDecimal;

public record FundingRateView(
        String symbol,
        String base,
        String quote,
        String exchange,
        BigDecimal fundingRate,
        long nextFundingTs,
        String link
) {
    public static FundingRateView of(FundingRateData fr) {
        return new FundingRateView(
                fr.symbol(),
                fr.instrument().baseAsset(),
                fr.instrument().quoteAsset(),
                fr.exchange().name(),
                fr.fundingRate(),
                fr.nextFundingTs(),
                ExchangeLinkResolver.link(fr.exchange(), fr.instrument())
        );
    }
}
