package net.protsenko.fundy.app.dto.rs;

import net.protsenko.fundy.app.domain.CanonicalInstrument;
import net.protsenko.fundy.app.exchange.ExchangeType;

import java.math.BigDecimal;
import java.util.Map;

public record SpotFuturesArbitrageData(
        CanonicalInstrument instrument,
        ExchangeType buyExchange,
        ExchangeType shortExchange,
        BigDecimal buyPrice,
        BigDecimal shortPrice,
        BigDecimal fundingRate,
        Long nextFundingTs,
        BigDecimal priceSpread,
        Map<ExchangeType, String> links
) {
}