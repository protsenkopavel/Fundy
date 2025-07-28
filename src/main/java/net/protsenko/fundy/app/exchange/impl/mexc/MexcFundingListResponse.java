package net.protsenko.fundy.app.exchange.impl.mexc;

import java.util.List;

public record MexcFundingListResponse(
        int code,
        String msg,
        List<MexcFundingItem> data
) {
}
