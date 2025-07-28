package net.protsenko.fundy.app.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.protsenko.fundy.app.dto.FundingRateData;
import net.protsenko.fundy.app.exchange.ExchangeClient;
import net.protsenko.fundy.app.exchange.ExchangeClientFactory;
import net.protsenko.fundy.app.exchange.ExchangeType;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class FundingScannerService {

    private final ExchangeClientFactory factory;

    public List<FundingRateData> findHighFundingRates(ExchangeType type, double minRateAbs) {
        ExchangeClient client = factory.getClient(type);
        BigDecimal th = BigDecimal.valueOf(Math.abs(minRateAbs));

        List<FundingRateData> all = client.getAllFundingRates();

        if (all.isEmpty()) {
            log.warn("No funding rates from {} (instruments={})", type, client.getAvailableInstruments().size());
            return List.of();
        }

        all.stream()
                .sorted(Comparator.comparing(fr -> fr.fundingRate().abs(), Comparator.reverseOrder()))
                .limit(10)
                .forEach(fr -> log.info("{} {} -> {}", type, fr.instrument().nativeSymbol(), fr.fundingRate()));

        return all.stream()
                .filter(fr -> fr.fundingRate().abs().compareTo(th) >= 0)
                .sorted(Comparator.comparing((FundingRateData fr) -> fr.fundingRate().abs()).reversed())
                .toList();
    }
}
