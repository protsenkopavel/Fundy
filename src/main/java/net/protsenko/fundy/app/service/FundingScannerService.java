package net.protsenko.fundy.app.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.protsenko.fundy.app.dto.FundingRateData;
import net.protsenko.fundy.app.dto.FundingRateEx;
import net.protsenko.fundy.app.exchange.ExchangeClient;
import net.protsenko.fundy.app.exchange.ExchangeClientFactory;
import net.protsenko.fundy.app.exchange.ExchangeType;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class FundingScannerService {

    private final ExchangeClientFactory factory;

    public List<FundingRateData> findHighFundingRates(
            ExchangeType type, double minRateAbs) {
        ExchangeClient client = factory.getClient(type);
        return scan(client, minRateAbs);
    }

    public List<FundingRateEx> findHighFundingRatesAll(double minRateAbs) {

        BigDecimal threshold = BigDecimal.valueOf(Math.abs(minRateAbs));

        return Arrays.stream(ExchangeType.values())
                .parallel()
                .flatMap(type -> {
                    try {
                        ExchangeClient c = factory.getClient(type);
                        return c.getAllFundingRates().stream()
                                .filter(fr -> fr.fundingRate().abs()
                                        .compareTo(threshold) >= 0)
                                .map(fr -> new FundingRateEx(type, fr));
                    } catch (Exception e) {
                        log.warn("Skip {}: {}", type, e.getMessage());
                        return Stream.empty();
                    }
                })
                .sorted(Comparator.comparing(
                                (FundingRateEx x) -> x.data()
                                        .fundingRate()
                                        .abs())
                        .reversed())
                .toList();
    }

    private List<FundingRateData> scan(
            ExchangeClient client, double minRateAbs) {

        BigDecimal th = BigDecimal.valueOf(Math.abs(minRateAbs));
        List<FundingRateData> all = client.getAllFundingRates();

        return all.stream()
                .filter(fr -> fr.fundingRate().abs().compareTo(th) >= 0)
                .sorted(Comparator.comparing(
                                (FundingRateData fr) -> fr.fundingRate().abs())
                        .reversed())
                .toList();
    }
}
