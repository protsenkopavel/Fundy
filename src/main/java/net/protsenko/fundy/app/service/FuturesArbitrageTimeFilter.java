package net.protsenko.fundy.app.service;

import net.protsenko.fundy.app.exchange.ExchangeType;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class FuturesArbitrageTimeFilter {
    public Set<ExchangeType> getExchangesWithSameAccrualTime(Map<ExchangeType, Long> nextFundingTsMap) {
        if (nextFundingTsMap == null || nextFundingTsMap.isEmpty()) {
            return Set.of();
        }

        Map<Long, Set<ExchangeType>> timeToExchanges = new HashMap<>();
        for (Map.Entry<ExchangeType, Long> entry : nextFundingTsMap.entrySet()) {
            if (entry.getValue() != null) {
                timeToExchanges.computeIfAbsent(entry.getValue(), k -> new HashSet<>()).add(entry.getKey());
            }
        }

        return timeToExchanges.values().stream()
                .max(Comparator.comparingInt(Set::size))
                .orElse(Set.of());
    }
}