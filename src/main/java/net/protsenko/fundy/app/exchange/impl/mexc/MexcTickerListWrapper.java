package net.protsenko.fundy.app.exchange.impl.mexc;

import java.util.List;

public record MexcTickerListWrapper(
        boolean success,
        int code,
        String msg,
        List<MexcTickerItem> data
) {
}
