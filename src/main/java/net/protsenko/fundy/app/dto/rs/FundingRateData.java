package net.protsenko.fundy.app.dto.rs;

import net.protsenko.fundy.app.exchange.ExchangeType;

import java.math.BigDecimal;

public record FundingRateData(
        ExchangeType exchange,
        InstrumentData instrument,
        BigDecimal fundingRate,
        Long nextFundingTs
) { }
