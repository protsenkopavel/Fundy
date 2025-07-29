package net.protsenko.fundy.notifier.dto;

import net.protsenko.fundy.app.exchange.ExchangeType;

public record AlertKey(
        long chatId,
        ExchangeType exchange,
        String nativeSymbol,
        long timeBucket
) {
}

