package net.protsenko.fundy.app.exchange.impl.kucoin;

public record KucoinContractSingleResponse(
        String code,
        KucoinContractItem data
) {
}
