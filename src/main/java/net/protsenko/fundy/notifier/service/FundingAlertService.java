package net.protsenko.fundy.notifier.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.protsenko.fundy.app.dto.FundingRateData;
import net.protsenko.fundy.app.exchange.ExchangeClient;
import net.protsenko.fundy.app.exchange.ExchangeClientFactory;
import net.protsenko.fundy.app.exchange.ExchangeType;
import net.protsenko.fundy.notifier.bot.TelegramSender;
import net.protsenko.fundy.notifier.dto.AlertKey;
import net.protsenko.fundy.notifier.dto.FundingAlertSettings;
import net.protsenko.fundy.notifier.repo.AlertSentStore;
import net.protsenko.fundy.notifier.repo.UserSettingsRepo;
import net.protsenko.fundy.notifier.util.FundingMessageFormatter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class FundingAlertService {

    private final ExchangeClientFactory factory;
    private final UserSettingsRepo settingsRepo;
    private final AlertSentStore sentStore;
    private final TelegramSender tg;

    @Value("${fundy.notifier.scan-delay:PT2M}")
    private String scanDelay; // для логов

    @Scheduled(fixedDelayString = "${fundy.notifier.scan-delay:PT2M}")
    public void scanAndNotify() {
        Map<ExchangeType, List<FundingRateData>> snapshot = snapshotAll();

        for (FundingAlertSettings s : settingsRepo.findAll()) {
            processUser(s, snapshot);
        }
    }

    private Map<ExchangeType, List<FundingRateData>> snapshotAll() {
        Map<ExchangeType, List<FundingRateData>> res = new EnumMap<>(ExchangeType.class);
        for (ExchangeType t : ExchangeType.values()) {
            try {
                ExchangeClient c = factory.getClient(t);
                if (!c.isEnabled()) { res.put(t, List.of()); continue; }
                res.put(t, c.getAllFundingRates());
            } catch (Exception e) {
                log.warn("Funding snapshot failed for {}", t, e);
                res.put(t, List.of());
            }
        }
        return res;
    }

    private void processUser(FundingAlertSettings s, Map<ExchangeType, List<FundingRateData>> snap) {
        long now = System.currentTimeMillis();
        for (Map.Entry<ExchangeType, List<FundingRateData>> e : snap.entrySet()) {
            ExchangeType ex = e.getKey();
            if (!s.exchanges().isEmpty() && !s.exchanges().contains(ex)) continue;

            for (FundingRateData fr : e.getValue()) {
                BigDecimal abs = fr.fundingRate().abs();
                if (abs.compareTo(s.minAbsRate()) < 0) continue;

                long left = fr.nextFundingTimeMs() - now;
                if (left < 0 || left > s.notifyBefore().toMillis()) continue;

                AlertKey key = new AlertKey(ex, fr.instrument().nativeSymbol(), fr.nextFundingTimeMs());
                if (!sentStore.markIfNotSent(key)) continue;

                String msg = FundingMessageFormatter.format(fr, ex, s.zone());
                tg.sendMessage(s.chatId(), msg);
            }
        }
    }
}
