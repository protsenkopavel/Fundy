package net.protsenko.fundy.app.dto.rs;

import com.fasterxml.jackson.annotation.JsonProperty;
import net.protsenko.fundy.app.dto.CanonicalInstrument;
import net.protsenko.fundy.app.exchange.ExchangeType;

import java.math.BigDecimal;
import java.util.Map;

public record ArbitrageData(
        CanonicalInstrument instrument,
        Map<ExchangeType, BigDecimal> prices,
        Map<ExchangeType, BigDecimal> fundingRates,
        Map<ExchangeType, Long> nextFundingTs,
        BigDecimal priceSpread,
        BigDecimal fundingSpread,
        Decision decision,
        Map<ExchangeType, String> links
) {
    @JsonProperty("token")
    public String token() {
        return instrument.canonicalKey();
    }

    public record Decision(ExchangeType longEx, ExchangeType shortEx) {
    }
}
