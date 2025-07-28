package net.protsenko.fundy.app.exchange.impl.coinex;

import java.util.Map;

public record CoinexTickerAllData(
        long date,
        Map<String, CoinexTickerItem> ticker
) {
}
