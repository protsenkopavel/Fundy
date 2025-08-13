package net.protsenko.fundy.app.exchange.impl.okx;

public record OkxTickerItem(
        String instId,
        String instType,
        String last,
        String askPx,
        String bidPx,
        String high24h,
        String low24h,
        String vol24h,
        String ts
) {
}
