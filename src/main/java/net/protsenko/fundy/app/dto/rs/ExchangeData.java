package net.protsenko.fundy.app.dto.rs;

import net.protsenko.fundy.app.exchange.ExchangeType;

public record ExchangeData(
        ExchangeType type,
        String name
) {
}