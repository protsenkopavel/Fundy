package net.protsenko.fundy.notifier.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.protsenko.fundy.app.dto.FundingRateData;
import net.protsenko.fundy.app.exchange.ExchangeType;
import net.protsenko.fundy.notifier.bot.TelegramSender;
import net.protsenko.fundy.notifier.dto.AlertKey;
import net.protsenko.fundy.notifier.dto.AlertLastRateStore;
import net.protsenko.fundy.notifier.dto.FundingAlertSettings;
import net.protsenko.fundy.notifier.repo.AlertSentStore;
import net.protsenko.fundy.notifier.repo.UserSettingsRepo;
import net.protsenko.fundy.notifier.util.FundingMessageFormatter;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class FundingAlertService {

    private static final BigDecimal DELTA_MIN = new BigDecimal("0.001");
    private final FundingAggregatorService aggregator;
    private final UserSettingsRepo settingsRepo;
    private final AlertSentStore sentStore;
    private final AlertLastRateStore lastStore;
    private final TelegramSender tg;

    @Scheduled(fixedDelayString = "${fundy.notifier.scan-delay:PT2M}")
    public void scanAndNotify() {
        Map<ExchangeType, List<FundingRateData>> snapshot = aggregator.snapshotAll();
        settingsRepo.findAll().forEach(s -> processUser(s, snapshot));
    }

    /* ---------- helpers ---------- */

    private void processUser(FundingAlertSettings s,
                             Map<ExchangeType, List<FundingRateData>> snap) {

        long bucket = System.currentTimeMillis() / Duration.ofMinutes(15).toMillis();

        for (var e : snap.entrySet()) {
            ExchangeType ex = e.getKey();
            if (!s.exchanges().isEmpty() && !s.exchanges().contains(ex)) continue;

            for (FundingRateData fr : e.getValue()) {
                if (fr.fundingRate().abs().compareTo(s.minAbsRate()) < 0) continue;
                if (timeTooEarlyOrLate(fr, s)) continue;

                AlertKey key = new AlertKey(s.chatId(), ex,
                        fr.instrument().nativeSymbol(), bucket);

                BigDecimal newRate = fr.fundingRate();

                /* ---------- первый раз в бакете ---------- */
                if (sentStore.markIfNotSent(key)) {
                    sendNew(s, fr, ex, newRate);               // ⚡
                    lastStore.put(key, newRate);
                    continue;
                }

                /* ---------- обновление ---------- */
                BigDecimal prev = lastStore.get(key).orElse(newRate);
                BigDecimal delta = newRate.subtract(prev).abs();

                if (delta.compareTo(DELTA_MIN) >= 0) {
                    sendUpdate(s, fr, ex, prev, newRate);      // 🔄
                    lastStore.put(key, newRate);
                }
            }
        }
    }

    /* util */
    private void sendNew(FundingAlertSettings s, FundingRateData fr,
                         ExchangeType ex, BigDecimal rate) {
        String head = "⚡ <b>Новый фандинг > %s</b>:\n\n"
                .formatted(FundingMessageFormatter.pct(s.minAbsRate()));
        tg.sendMessage(s.chatId(), head +
                FundingMessageFormatter.format(fr, ex, s.zone()));
    }

    private void sendUpdate(FundingAlertSettings s,
                            FundingRateData fr,
                            ExchangeType ex,
                            BigDecimal prev,
                            BigDecimal now) {

        // ↑ если ставка выросла, ↓ если упала (0 = «нет изменений», тогда не шлём)
        String arrow = now.compareTo(prev) > 0 ? "↑" : "↓";
        if (now.compareTo(prev) == 0) return;   // нечего обновлять

        // подставляем стрелку сразу после процента
        String newPct = FundingMessageFormatter.pct(now) + arrow;

        String body = FundingMessageFormatter.format(fr, ex, s.zone())
                .replace(FundingMessageFormatter.pct(now), newPct);

        tg.sendMessage(s.chatId(), "🔄 <b>Обновление:</b>\n\n" + body);
    }

    private boolean timeTooEarlyOrLate(FundingRateData fr,
                                       FundingAlertSettings s) {

        long leftMs = fr.nextFundingTimeMs() - System.currentTimeMillis();
        return leftMs < 0                    // начисление уже прошло
                || leftMs > s.notifyBefore().toMillis(); // ещё далеко до события
    }
}
