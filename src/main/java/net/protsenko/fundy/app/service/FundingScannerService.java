package net.protsenko.fundy.app.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.protsenko.fundy.app.dto.rq.FundingFilterRequest;
import net.protsenko.fundy.app.dto.rs.FundingRateData;
import net.protsenko.fundy.app.exchange.ExchangeClientFactory;
import net.protsenko.fundy.app.exchange.ExchangeType;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class FundingScannerService {
    private final ExchangeClientFactory factory;

    public List<FundingRateData> getFundingOpportunities(FundingFilterRequest req) {
        return req.effectiveExchanges().parallelStream()
                .flatMap(ex -> loadExchangeData(ex, req.minFr()))
                .sorted(Comparator.comparing((FundingRateData r) -> r.fundingRate().abs()).reversed())
                .toList();
    }

    private Stream<FundingRateData> loadExchangeData(ExchangeType ex, BigDecimal minFr) {
        try {
            return factory.getClient(ex).getAllFundingRates().stream()
                    .filter(fr -> fr.fundingRate().abs().compareTo(minFr) >= 0);
        } catch (Exception e) {
            log.warn("Skip {}: {}", ex, e.getMessage());
            return Stream.empty();
        }
    }
}
