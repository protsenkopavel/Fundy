package net.protsenko.fundy.app.exchange.impl.bitget;

public record BitgetContractItem(
        String symbol,
        String symbolDisplayName,
        String symbolName,
        String baseCoin,
        String quoteCoin,
        String symbolType,
        String symbolStatus,
        String makerFeeRate,
        String takerFeeRate,
        String minTradeNum,
        String priceEndStep,
        String volumePlace,
        String pricePlace,
        String sizeMultiplier
) {
}