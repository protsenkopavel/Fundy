package net.protsenko.fundy.notifier.repo;

import lombok.extern.slf4j.Slf4j;
import net.protsenko.fundy.notifier.dto.AlertKey;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class AlertSentStore {

    private static final long TTL_MS = Duration.ofHours(12).toMillis();

    /** key -> timestamp ms */
    private final Map<AlertKey, Long> sent = new ConcurrentHashMap<>();

    /** @return true, если такого ещё не было (и мы его только что поместили) */
    public boolean markIfNotSent(AlertKey key) {
        long now = System.currentTimeMillis();
        return sent.compute(key, (k, v) -> (v == null || now - v > TTL_MS) ? now : v) == now;
    }

    /** Периодически чистим старые записи */
    @Scheduled(fixedDelayString = "PT1H")
    public void cleanup() {
        long now = System.currentTimeMillis();
        sent.entrySet().removeIf(e -> now - e.getValue() > TTL_MS);
        log.debug("AlertSentStore cleanup, size={}", sent.size());
    }
}