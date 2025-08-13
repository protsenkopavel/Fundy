package net.protsenko.fundy.app.exchange.impl.gateio;

public record GateioContractItem(
        String name,
        String status,
        String asset,
        String currency,
        String fundingRate,
        String fundingRateIndicative,
        long fundingNextApply,
        int fundingInterval,
        String markPrice,
        String lastPrice
) {
}
