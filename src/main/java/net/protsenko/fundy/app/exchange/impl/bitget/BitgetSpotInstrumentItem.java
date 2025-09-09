package net.protsenko.fundy.app.exchange.impl.bitget;

public record BitgetSpotInstrumentItem(
        String symbol,
        String symbolName,
        String baseCoin,
        String quoteCoin,
        String status,
        String minTradeAmount,
        String maxTradeAmount,
        String takerFeeRate,
        String makerFeeRate,
        String priceScale,
        String quantityScale,
        String minTradeUSDT
) {
}