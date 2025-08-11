package net.protsenko.fundy.app.dto.rs;

import net.protsenko.fundy.app.dto.InstrumentType;
import net.protsenko.fundy.app.exchange.ExchangeType;

public record InstrumentData(
        String baseAsset,
        String quoteAsset,
        InstrumentType type,
        String nativeSymbol,
        ExchangeType exchangeType
) {
}

