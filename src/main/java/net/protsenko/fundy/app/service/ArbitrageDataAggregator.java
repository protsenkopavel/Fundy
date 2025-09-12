package net.protsenko.fundy.app.service;

import lombok.RequiredArgsConstructor;
import net.protsenko.fundy.app.dto.rs.FundingRateData;
import net.protsenko.fundy.app.dto.rs.TickerData;
import net.protsenko.fundy.app.exchange.ExchangeType;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class ArbitrageDataAggregator {

    private final FuturesService futuresService;
    private final FundingService fundingService;

    public MarketData collectMarketData(Set<ExchangeType> exchanges) {
        Map<String, Map<ExchangeType, TickerData>> priceData =
                futuresService.collectFuturesPriceData(exchanges);

        Map<String, Map<ExchangeType, FundingRateData>> fundingData =
                fundingService.collectFundingData(exchanges);

        return new MarketData(priceData, fundingData);
    }

    public record MarketData(
            Map<String, Map<ExchangeType, TickerData>> priceData,
            Map<String, Map<ExchangeType, FundingRateData>> fundingData
    ) {
    }
}