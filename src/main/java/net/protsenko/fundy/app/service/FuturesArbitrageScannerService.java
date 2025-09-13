package net.protsenko.fundy.app.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.protsenko.fundy.app.domain.ArbitrageOpportunity;
import net.protsenko.fundy.app.domain.CanonicalInstrument;
import net.protsenko.fundy.app.domain.FundingSpread;
import net.protsenko.fundy.app.domain.PriceSpread;
import net.protsenko.fundy.app.dto.rq.FuturesArbitrageRequest;
import net.protsenko.fundy.app.dto.rs.FuturesArbitrageData;
import net.protsenko.fundy.app.dto.rs.FundingRateData;
import net.protsenko.fundy.app.dto.rs.TickerData;
import net.protsenko.fundy.app.exchange.ExchangeType;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

import static net.protsenko.fundy.app.utils.ExchangeLinkResolver.generateTradingLinks;

@Slf4j
@Service
@RequiredArgsConstructor
public class FuturesArbitrageScannerService {

    private final FuturesArbitrageDataAggregator dataAggregator;
    private final FuturesArbitrageCalculator calculator;
    private final FuturesArbitrageAnalyzer analyzer;
    private final FuturesArbitrageTimeFilter timeFilter;

    public List<FuturesArbitrageData> getArbitrageOpportunities(FuturesArbitrageRequest req) {
        Set<ExchangeType> exchanges = req.effectiveExchanges();
        BigDecimal minVolume = req.effectiveMinVolume();
        FuturesArbitrageDataAggregator.MarketData marketData = dataAggregator.collectMarketData(exchanges);

        return marketData.priceData().entrySet().parallelStream()
                .filter(entry -> {
                    Map<ExchangeType, TickerData> instrumentPrices = entry.getValue();
                    return instrumentPrices != null && instrumentPrices.size() >= 2;
                })
                .map(entry -> {
                    String canonicalKey = entry.getKey();
                    Map<ExchangeType, TickerData> instrumentPrices = entry.getValue();
                    Map<ExchangeType, FundingRateData> instrumentFunding = marketData.fundingData().get(canonicalKey);

                    return analyzeArbitrageOpportunity(canonicalKey, instrumentPrices, instrumentFunding, req);
                })
                .filter(Objects::nonNull)
                .filter(data -> {
                    FuturesArbitrageData.Decision decision = data.decision();
                    if (decision == null) return false;
                    BigDecimal longVolume = data.volumes().get(decision.longEx());
                    BigDecimal shortVolume = data.volumes().get(decision.shortEx());
                    return (longVolume != null && longVolume.compareTo(minVolume) >= 0) &&
                           (shortVolume != null && shortVolume.compareTo(minVolume) >= 0);
                })
                .sorted((a, b) -> {
                    BigDecimal spreadA = calculator.calculateCombinedSpread(a);
                    BigDecimal spreadB = calculator.calculateCombinedSpread(b);
                    return spreadB.compareTo(spreadA);
                })
                .collect(Collectors.toList());
    }

    private FuturesArbitrageData analyzeArbitrageOpportunity(
            String canonicalKey,
            Map<ExchangeType, TickerData> prices,
            Map<ExchangeType, FundingRateData> fundingRates,
            FuturesArbitrageRequest req) {

        if (prices.size() < 2) return null;

        String[] parts = canonicalKey.split("/");
        if (parts.length < 2) return null;
        CanonicalInstrument instrument = new CanonicalInstrument(parts[0], parts[1]);

        List<PriceSpread> priceSpreads = calculator.calculatePriceSpreads(prices);

        List<FundingSpread> fundingSpreads = calculator.calculateFundingSpreads(fundingRates);

        ArbitrageOpportunity bestOpportunity = analyzer.findBestArbitrageOpportunity(
                priceSpreads, fundingSpreads, req);

        if (bestOpportunity == null) return null;

        Map<ExchangeType, BigDecimal> priceMap = prices.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> e.getValue().lastPrice()
                ));

        Map<ExchangeType, BigDecimal> volumeMap = prices.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> e.getValue().volume24h()
                ));

        Map<ExchangeType, BigDecimal> fundingMap = fundingRates != null ?
                fundingRates.entrySet().stream()
                        .collect(Collectors.toMap(
                                Map.Entry::getKey,
                                e -> e.getValue().fundingRate()
                        )) : new EnumMap<>(ExchangeType.class);

        Map<ExchangeType, Long> nextFundingTsMap = fundingRates != null ?
                fundingRates.entrySet().stream()
                        .collect(Collectors.toMap(
                                Map.Entry::getKey,
                                e -> e.getValue().nextFundingTs()
                        )) : new EnumMap<>(ExchangeType.class);

        if (req.sameAccrualTime() != null && req.sameAccrualTime()) {
            Set<ExchangeType> validExchanges = timeFilter.getExchangesWithSameAccrualTime(nextFundingTsMap);
            if (validExchanges.size() < 2) {
                return null;
            }

            prices = prices.entrySet().stream()
                    .filter(entry -> validExchanges.contains(entry.getKey()))
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

            if (fundingRates != null) {
                fundingRates = fundingRates.entrySet().stream()
                        .filter(entry -> validExchanges.contains(entry.getKey()))
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
            }

            priceSpreads = calculator.calculatePriceSpreads(prices);
            fundingSpreads = calculator.calculateFundingSpreads(fundingRates);
            bestOpportunity = analyzer.findBestArbitrageOpportunity(priceSpreads, fundingSpreads, req);

            if (bestOpportunity == null) return null;
        }

        Map<ExchangeType, String> nativeSymbols = prices.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> e.getValue().instrument().nativeSymbol()
                ));
        Map<ExchangeType, String> links = generateTradingLinks(instrument, nativeSymbols);

        return new FuturesArbitrageData(
                instrument,
                priceMap,
                volumeMap,
                fundingMap,
                nextFundingTsMap,
                bestOpportunity.priceSpread(),
                bestOpportunity.fundingSpread(),
                new FuturesArbitrageData.Decision(bestOpportunity.longExchange(), bestOpportunity.shortExchange()),
                links
        );
    }
}