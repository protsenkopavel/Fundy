package net.protsenko.fundy.notifier.service;

import lombok.RequiredArgsConstructor;
import net.protsenko.fundy.app.dto.rs.FundingRateData;
import net.protsenko.fundy.app.exchange.ExchangeClient;
import net.protsenko.fundy.app.exchange.ExchangeClientFactory;
import net.protsenko.fundy.app.exchange.ExchangeType;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class FundingAggregatorService {

    private final ExchangeClientFactory factory;

    public Map<ExchangeType, List<FundingRateData>> snapshotAll() {
        Map<ExchangeType, List<FundingRateData>> res = new EnumMap<>(ExchangeType.class);
        for (ExchangeType t : ExchangeType.values()) {
            try {
                ExchangeClient c = factory.getClient(t);
                if (!c.isEnabled()) {
                    res.put(t, List.of());
                } else {
                    res.put(t, c.getAllFundingRates());
                }
            } catch (Exception e) {
                res.put(t, List.of());
            }
        }
        return res;
    }
}
