package net.protsenko.fundy.app.dto;

public record TickerData(
        TradingInstrument instrument,
        double lastPrice,
        double bid,
        double ask,
        double high24h,
        double low24h,
        double volume24h,
        long timestamp
) {
}

