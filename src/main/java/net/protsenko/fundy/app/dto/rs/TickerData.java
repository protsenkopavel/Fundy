package net.protsenko.fundy.app.dto.rs;

import java.math.BigDecimal;

public record TickerData(
        InstrumentData instrument,
        BigDecimal lastPrice,
        BigDecimal bid,
        BigDecimal ask,
        BigDecimal high24h,
        BigDecimal low24h,
        BigDecimal volume24h,
        long timestamp
) {
}

