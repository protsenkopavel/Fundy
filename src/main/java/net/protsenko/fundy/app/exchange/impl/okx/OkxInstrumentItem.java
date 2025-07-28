package net.protsenko.fundy.app.exchange.impl.okx;

public record OkxInstrumentItem(
        String instId,
        String instType,
        String state,
        String baseCcy,
        String quoteCcy,
        String ctType,
        String settleCcy
) {
}
