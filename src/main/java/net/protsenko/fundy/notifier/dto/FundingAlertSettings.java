package net.protsenko.fundy.notifier.dto;

import net.protsenko.fundy.app.exchange.ExchangeType;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.ZoneId;
import java.util.Set;

public record FundingAlertSettings(
        long chatId,
        BigDecimal minAbsRate,
        Set<ExchangeType> exchanges,
        Duration notifyBefore,
        ZoneId zone
) {
}
