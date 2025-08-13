package net.protsenko.fundy.app.exchange.impl.htx;

public record HtxContractItem(
        String symbol,
        String contractCode,
        double contractSize,
        double priceTick,
        int contractStatus,
        String tradePartition,
        String businessType,
        String contractType
) {
}
