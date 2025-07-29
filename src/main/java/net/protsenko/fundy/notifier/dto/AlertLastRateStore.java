package net.protsenko.fundy.notifier.dto;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class AlertLastRateStore {
    private final Map<AlertKey, BigDecimal> map = new ConcurrentHashMap<>();

    public BigDecimal put(AlertKey k, BigDecimal rate) {
        return map.put(k, rate);
    }

    public Optional<BigDecimal> get(AlertKey k) {
        return Optional.ofNullable(map.get(k));
    }
}
