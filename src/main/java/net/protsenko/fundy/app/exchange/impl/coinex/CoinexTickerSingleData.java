package net.protsenko.fundy.app.exchange.impl.coinex;

public record CoinexTickerSingleData(
        long date,
        CoinexTickerItem ticker
) {
}
