package net.protsenko.fundy.app.dto.rs;

import com.fasterxml.jackson.annotation.JsonIgnore;
import net.protsenko.fundy.app.exchange.ExchangeType;
import net.protsenko.fundy.app.utils.SymbolNormalizer;

import java.math.BigDecimal;


public record FundingRateData(
        InstrumentData instrument,
        BigDecimal fundingRate,
        long nextFundingTs
) {
    public ExchangeType exchange() {
        return instrument.exchangeType();
    }

    public String symbol() {
        return instrument.nativeSymbol() != null
                ? instrument.nativeSymbol()
                : instrument.baseAsset() + instrument.quoteAsset();
    }

    @JsonIgnore
    public String canonicalKey() {
        return SymbolNormalizer.canonicalKey(instrument);
    }
}
