package net.protsenko.fundy.app.exchange.impl.mexc;

import java.util.List;

public record MexcInstrumentsResponse(
        int code,
        String msg,
        List<MexcInstrumentItem> data
) {
}
