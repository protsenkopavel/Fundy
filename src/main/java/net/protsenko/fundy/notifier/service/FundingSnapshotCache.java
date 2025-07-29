package net.protsenko.fundy.notifier.service;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.protsenko.fundy.app.dto.FundingRateData;
import net.protsenko.fundy.app.exchange.ExchangeType;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
@RequiredArgsConstructor
public class FundingSnapshotCache {

    private final FundingAggregatorService aggregator;

    private final ExecutorService exec = Executors.newVirtualThreadPerTaskExecutor();
    private final AtomicBoolean refreshing = new AtomicBoolean(false);

    @Getter
    private volatile Map<ExchangeType, List<FundingRateData>> lastSnapshot = Map.of();
    @Getter
    private volatile Instant lastUpdated = Instant.EPOCH;

    /** Считаем кэш «устаревшим» после N минут */
    private static final Duration STALE_AFTER = Duration.ofMinutes(5);

    /** Первый прогон после старта — в фоне */
    @EventListener(ApplicationReadyEvent.class)
    public void firstRefreshAsync() {
        exec.execute(this::safeRefresh);
    }

    /** Периодическое обновление */
    @Scheduled(initialDelayString = "PT2M", fixedDelayString = "PT2M")
    public void scheduledRefresh() {
        safeRefresh();
    }

    /** Признак, что данные старые */
    public boolean isStale() {
        return Instant.now().minus(STALE_AFTER).isAfter(lastUpdated);
    }

    /**
     * Форс-обновление с таймаутом (используем из бота, если кэш пустой/старый)
     */
    public Map<ExchangeType, List<FundingRateData>> forceRefresh(Duration timeout) {
        if (refreshing.compareAndSet(false, true)) {
            try {
                Future<Map<ExchangeType, List<FundingRateData>>> f = exec.submit(aggregator::snapshotAll);
                Map<ExchangeType, List<FundingRateData>> snap = f.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
                lastSnapshot = new EnumMap<>(snap);
                lastUpdated = Instant.now();
                log.debug("Snapshot force refreshed {}", lastUpdated);
            } catch (Exception e) {
                log.warn("Force refresh failed", e);
            } finally {
                refreshing.set(false);
            }
        }
        return lastSnapshot;
    }

    /** Без блокировок контекста */
    private void safeRefresh() {
        if (!refreshing.compareAndSet(false, true)) return;
        try {
            Map<ExchangeType, List<FundingRateData>> snap = aggregator.snapshotAll();
            lastSnapshot = new EnumMap<>(snap);
            lastUpdated = Instant.now();
            log.debug("Snapshot refreshed {}", lastUpdated);
        } catch (Exception e) {
            log.warn("Snapshot refresh failed", e);
        } finally {
            refreshing.set(false);
        }
    }
}