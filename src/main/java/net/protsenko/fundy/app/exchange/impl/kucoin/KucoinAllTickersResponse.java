package net.protsenko.fundy.app.exchange.impl.kucoin;

import java.util.List;

public record KucoinAllTickersResponse(
        String code,
        List<KucoinTickerData> data
) {
}