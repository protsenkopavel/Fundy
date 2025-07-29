package net.protsenko.fundy.notifier.dto;

import net.protsenko.fundy.app.exchange.ExchangeType;

public record AlertKey(
        ExchangeType exchange,
        String nativeSymbol,
        long fundingTimeMs
) {
}

