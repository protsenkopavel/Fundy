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
        ZoneId zone,
        Duration bucketSize
) {
    public FundingAlertSettings withMinAbsRate(BigDecimal rate) {
        return new FundingAlertSettings(chatId, rate, exchanges, notifyBefore, zone, bucketSize);
    }

    public FundingAlertSettings withNotifyBefore(Duration d) {
        return new FundingAlertSettings(chatId, minAbsRate, exchanges, d, zone, bucketSize);
    }

    public FundingAlertSettings withZone(ZoneId z) {
        return new FundingAlertSettings(chatId, minAbsRate, exchanges, notifyBefore, z, bucketSize);
    }

    public FundingAlertSettings withBucket(Duration d) {
        return new FundingAlertSettings(chatId, minAbsRate, exchanges,
                notifyBefore, zone, d);
    }
}
