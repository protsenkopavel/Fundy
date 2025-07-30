package net.protsenko.fundy.notifier.repo;

import net.protsenko.fundy.notifier.dto.FundingAlertSettings;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.ZoneId;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class UserSettingsRepo {
    private final Map<Long, FundingAlertSettings> map = new ConcurrentHashMap<>();

    public FundingAlertSettings getOrDefault(long chatId) {
        return map.getOrDefault(chatId,
                new FundingAlertSettings(chatId,
                        new BigDecimal("0.005"),
                        Set.of(),
                        Duration.ofMinutes(30),
                        ZoneId.systemDefault(),
                        Duration.ofMinutes(60)
                ));
    }

    public void save(FundingAlertSettings s) {
        map.put(s.chatId(), s);
    }

    public Collection<FundingAlertSettings> findAll() {
        return map.values();
    }
}