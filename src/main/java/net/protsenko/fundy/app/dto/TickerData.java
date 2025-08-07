package net.protsenko.fundy.app.dto;

import java.math.BigDecimal;

public record TickerData(
        TradingInstrument instrument,
        BigDecimal lastPrice,
        BigDecimal bid,
        BigDecimal ask,
        BigDecimal high24h,
        BigDecimal low24h,
        BigDecimal volume24h,
        long timestamp
) {
}

