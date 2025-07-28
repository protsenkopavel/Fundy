package net.protsenko.fundy.app.exchange.impl.bybit;

import java.util.List;

public record BybitTickerResponse(
        int retCode,
        String retMsg,
        Result result,
        long time
) {
    public record Result(List<BybitTickerItem> list) {
    }
}
