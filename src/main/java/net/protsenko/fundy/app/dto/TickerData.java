package net.protsenko.fundy.app.dto;

import java.math.BigDecimal;

public record TickerData(TradingInstrument instrument,
                         BigDecimal price,
                         BigDecimal volume24h
) {
}

