package net.protsenko.fundy.app.exchange.impl.bingx;

public record BingxContractItem(
        String contractId,
        String symbol,
        String size,
        int quantityPrecision,
        int pricePrecision,
        double makerFeeRate,
        double takerFeeRate,
        int status,
        String currency,
        String asset,
        long launchTime
) {
}