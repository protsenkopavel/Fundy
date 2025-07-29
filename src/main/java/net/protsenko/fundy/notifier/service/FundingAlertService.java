package net.protsenko.fundy.notifier.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.protsenko.fundy.app.dto.FundingRateData;
import net.protsenko.fundy.app.exchange.ExchangeType;
import net.protsenko.fundy.notifier.bot.TelegramSender;
import net.protsenko.fundy.notifier.dto.AlertKey;
import net.protsenko.fundy.notifier.dto.FundingAlertSettings;
import net.protsenko.fundy.notifier.repo.AlertSentStore;
import net.protsenko.fundy.notifier.repo.UserSettingsRepo;
import net.protsenko.fundy.notifier.util.FundingMessageFormatter;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class FundingAlertService {

    private final FundingAggregatorService aggregator;
    private final UserSettingsRepo settingsRepo;
    private final AlertSentStore sentStore;
    private final TelegramSender tg;

    @Scheduled(fixedDelayString = "${fundy.notifier.scan-delay:PT2M}")
    public void scanAndNotify() {
        Map<ExchangeType, List<FundingRateData>> snapshot = aggregator.snapshotAll();
        settingsRepo.findAll().forEach(s -> processUser(s, snapshot));
    }

    /* ---------- helpers ---------- */

    private void processUser(FundingAlertSettings s,
                             Map<ExchangeType, List<FundingRateData>> snap) {

        long now = System.currentTimeMillis();

        for (var e : snap.entrySet()) {
            ExchangeType ex = e.getKey();
            if (!s.exchanges().isEmpty() && !s.exchanges().contains(ex)) continue;

            for (FundingRateData fr : e.getValue()) {
                BigDecimal abs = fr.fundingRate().abs();
                if (abs.compareTo(s.minAbsRate()) < 0) continue;

                long left = fr.nextFundingTimeMs() - now;
                if (left < 0 || left > s.notifyBefore().toMillis()) continue;

                long bucket = fr.nextFundingTimeMs() / (15 * 60_000);
                AlertKey key = new AlertKey(s.chatId(), ex, fr.instrument().nativeSymbol(), bucket);

                if (!sentStore.markIfNotSent(key)) continue;

                String body = FundingMessageFormatter.format(fr, ex, s.zone());
                String head = "⚡ <b>Новый фандинг > %s</b>:\n\n"
                        .formatted(FundingMessageFormatter.pct(s.minAbsRate()));
                tg.sendMessage(s.chatId(), head + body);
            }
        }
    }
}
