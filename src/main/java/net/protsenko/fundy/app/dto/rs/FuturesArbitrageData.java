package net.protsenko.fundy.app.dto.rs;

import net.protsenko.fundy.app.domain.CanonicalInstrument;
import net.protsenko.fundy.app.exchange.ExchangeType;

import java.math.BigDecimal;
import java.util.Map;

public record FuturesArbitrageData(
        CanonicalInstrument instrument,
        Map<ExchangeType, BigDecimal> prices,
        Map<ExchangeType, BigDecimal> fundingRates,
        Map<ExchangeType, Long> nextFundingTs,
        BigDecimal priceSpread,
        BigDecimal fundingSpread,
        Decision decision,
        Map<ExchangeType, String> links
) {
    public record Decision(ExchangeType longEx, ExchangeType shortEx) {
        public Decision {
        }
    }
}
