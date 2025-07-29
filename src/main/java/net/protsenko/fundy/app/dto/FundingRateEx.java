package net.protsenko.fundy.app.dto;

import net.protsenko.fundy.app.exchange.ExchangeType;

public record FundingRateEx(
        ExchangeType exchange,
        FundingRateData data
) {
}

