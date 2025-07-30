package net.protsenko.fundy.notifier.service;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.protsenko.fundy.app.dto.FundingRateData;
import net.protsenko.fundy.app.exchange.ExchangeType;
import net.protsenko.fundy.notifier.dto.SnapshotRefreshedEvent;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
public class FundingSnapshotCache {

    private static final Duration STALE_AFTER = Duration.ofMinutes(5);
    private final FundingAggregatorService aggregator;
    private final ApplicationEventPublisher publisher;
    private final ExecutorService exec = Executors.newVirtualThreadPerTaskExecutor();
    private final AtomicBoolean refreshing = new AtomicBoolean(false);
    @Getter
    private volatile Map<ExchangeType, List<FundingRateData>> lastSnapshot = Map.of();
    @Getter
    private volatile Instant lastUpdated = Instant.EPOCH;

    public FundingSnapshotCache(FundingAggregatorService aggregator,
                                ApplicationEventPublisher publisher) {
        this.aggregator = aggregator;
        this.publisher = publisher;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void firstRefreshAsync() {
        exec.execute(this::safeRefresh);
    }

    @Scheduled(initialDelayString = "PT2M", fixedDelayString = "PT2M")
    public void scheduledRefresh() {
        safeRefresh();
    }

    public boolean isStale() {
        return Instant.now().minus(STALE_AFTER).isAfter(lastUpdated);
    }

    public Map<ExchangeType, List<FundingRateData>> forceRefresh(Duration timeout) {
        if (refreshing.compareAndSet(false, true)) {
            try {
                Future<Map<ExchangeType, List<FundingRateData>>> f = exec.submit(aggregator::snapshotAll);
                Map<ExchangeType, List<FundingRateData>> snap = f.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
                lastSnapshot = new EnumMap<>(snap);
                lastUpdated = Instant.now();
                publisher.publishEvent(new SnapshotRefreshedEvent(lastUpdated));
            } catch (Exception e) {
                log.warn("Force refresh failed", e);
            } finally {
                refreshing.set(false);
            }
        }
        return lastSnapshot;
    }

    private void safeRefresh() {
        if (!refreshing.compareAndSet(false, true)) return;
        try {
            Map<ExchangeType, List<FundingRateData>> snap = aggregator.snapshotAll();
            lastSnapshot = new EnumMap<>(snap);
            lastUpdated = Instant.now();
            publisher.publishEvent(new SnapshotRefreshedEvent(lastUpdated));
            log.debug("Snapshot refreshed {}", lastUpdated);
        } catch (Exception e) {
            log.warn("Snapshot refresh failed", e);
        } finally {
            refreshing.set(false);
        }
    }
}