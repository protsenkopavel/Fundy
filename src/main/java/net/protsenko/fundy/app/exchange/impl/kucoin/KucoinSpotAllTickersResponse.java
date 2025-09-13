package net.protsenko.fundy.app.exchange.impl.kucoin;

import java.util.List;

public record KucoinSpotAllTickersResponse(
        String code,
        KucoinSpotTickersData data
) {
    public record KucoinSpotTickersData(
            long time,
            List<KucoinTickerData> ticker
    ) {
    }
}