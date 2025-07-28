package net.protsenko.fundy.app.service;

import lombok.RequiredArgsConstructor;
import net.protsenko.fundy.app.dto.FundingRateData;
import net.protsenko.fundy.app.exchange.ExchangeClientFactory;
import net.protsenko.fundy.app.exchange.ExchangeType;
import net.protsenko.fundy.app.exchange.impl.bybit.BybitExchangeClient;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class FundingScannerService {

    private final ExchangeClientFactory factory;

    public List<FundingRateData> findHighFundingRates(ExchangeType type, double minRateAbs) {
        if (type != ExchangeType.BYBIT) {
            throw new UnsupportedOperationException("Пока реализовано только для BYBIT");
        }
        var client = (BybitExchangeClient) factory.getClient(type);

        BigDecimal threshold = BigDecimal.valueOf(Math.abs(minRateAbs));

        return client.getAllFundingRates().stream()
                .filter(fr -> fr.fundingRate().abs().compareTo(threshold) >= 0)
                .sorted(Comparator.comparing((FundingRateData fr) -> fr.fundingRate().abs()).reversed())
                .toList();
    }
}
