package net.protsenko.fundy.app.exchange.impl.bingx;

import java.util.List;

public record BingxSpotInstrumentsResponse(
        List<BingxSpotInstrumentItem> symbols
) {
}