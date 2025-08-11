package net.protsenko.fundy.app.dto.rs;

import net.protsenko.fundy.app.exchange.ExchangeType;

import java.math.BigDecimal;

public record FundingRateData(
        ExchangeType exchange,
        String symbol,
        BigDecimal fundingRate,
        Long nextFundingTs
) {
    public FundingRateData(ExchangeType exchange, InstrumentData instrument, BigDecimal fundingRate, Long nextFundingTs) {
        this(exchange, instrument.nativeSymbol() != null ? instrument.nativeSymbol() : instrument.baseAsset() + instrument.quoteAsset(),
             fundingRate, nextFundingTs);
    }

    public String instrument() {
        return symbol;
    }
}
