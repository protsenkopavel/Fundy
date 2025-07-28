package net.protsenko.fundy.app.exchange.impl.kucoin;

public record KucoinTickerResponse(
        String code,
        KucoinTickerData data
) {
}