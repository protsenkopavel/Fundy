package net.protsenko.fundy.app.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.protsenko.fundy.app.domain.CanonicalInstrument;
import net.protsenko.fundy.app.dto.rq.SpotFuturesArbitrageRequest;
import net.protsenko.fundy.app.dto.rs.FundingRateData;
import net.protsenko.fundy.app.dto.rs.SpotFuturesArbitrageData;
import net.protsenko.fundy.app.dto.rs.TickerData;
import net.protsenko.fundy.app.exchange.ExchangeType;
import net.protsenko.fundy.app.utils.ExchangeLinkResolver;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SpotFuturesArbitrageService {

    private final SpotService spotService;
    private final FuturesService futuresService;
    private final FundingService fundingService;
    private final UniverseService universeService;

    public List<SpotFuturesArbitrageData> getSpotFuturesArbitrageOpportunities(SpotFuturesArbitrageRequest request) {
        Set<ExchangeType> exchanges = request.effectiveExchanges();
        BigDecimal minSpread = request.effectiveMinSpread();
        BigDecimal maxSpread = request.effectiveMaxSpread();
        BigDecimal minVolume = request.effectiveMinVolume();

        Map<String, Map<ExchangeType, TickerData>> spotPriceData = spotService.collectSpotPriceData(exchanges);
        Map<String, Map<ExchangeType, TickerData>> futuresPriceData = futuresService.collectFuturesPriceData(exchanges);
        Map<String, Map<ExchangeType, FundingRateData>> fundingData = fundingService.collectFundingData(exchanges);

        Map<String, Map<ExchangeType, String>> spotUniverse = universeService.spotUniverse(exchanges);
        Map<String, Map<ExchangeType, String>> futuresUniverse = universeService.perpUniverse(exchanges);

        return spotPriceData.entrySet().parallelStream()
                .filter(entry -> {
                    String canonicalKey = entry.getKey();
                    return futuresPriceData.containsKey(canonicalKey) &&
                           entry.getValue() != null && entry.getValue().size() >= 1 &&
                           futuresPriceData.get(canonicalKey) != null && futuresPriceData.get(canonicalKey).size() >= 1;
                })
                .map(entry -> {
                    String canonicalKey = entry.getKey();
                    Map<ExchangeType, TickerData> spotPrices = entry.getValue();
                    Map<ExchangeType, TickerData> futuresPrices = futuresPriceData.get(canonicalKey);
                    Map<ExchangeType, FundingRateData> fundingRates = fundingData.get(canonicalKey);
                    Map<ExchangeType, String> spotNativeSymbols = spotUniverse.get(canonicalKey);
                    Map<ExchangeType, String> futuresNativeSymbols = futuresUniverse.get(canonicalKey);
                    return analyzeSpotFuturesArbitrageOpportunity(canonicalKey, spotPrices, futuresPrices,
                            fundingRates, spotNativeSymbols, futuresNativeSymbols, exchanges);
                })
                .filter(Objects::nonNull)
                .filter(opportunity -> opportunity.priceSpread().compareTo(minSpread) >= 0)
                .filter(opportunity -> opportunity.priceSpread().compareTo(maxSpread) <= 0)
                .filter(opportunity -> opportunity.buyVolume24h().compareTo(minVolume) >= 0)
                .filter(opportunity -> opportunity.shortVolume24h().compareTo(minVolume) >= 0)
                .sorted((a, b) -> b.priceSpread().compareTo(a.priceSpread()))
                .collect(Collectors.toList());
    }

    private SpotFuturesArbitrageData analyzeSpotFuturesArbitrageOpportunity(
            String canonicalKey,
            Map<ExchangeType, TickerData> spotPrices,
            Map<ExchangeType, TickerData> futuresPrices,
            Map<ExchangeType, FundingRateData> fundingRates,
            Map<ExchangeType, String> spotNativeSymbols,
            Map<ExchangeType, String> futuresNativeSymbols,
            Set<ExchangeType> targetExchanges) {

        if (spotPrices.size() < 1 || futuresPrices.size() < 1) return null;

        ExchangePrice bestSpot = findBestSpotPrice(spotPrices, targetExchanges);
        if (bestSpot == null) return null;

        ExchangePrice bestFutures = findBestFuturesPrice(futuresPrices, targetExchanges);
        if (bestFutures == null) return null;

        if (bestSpot.exchange == bestFutures.exchange) return null;

        BigDecimal spread = calculatePriceSpread(bestFutures.price, bestSpot.price);

        BigDecimal fundingRate = BigDecimal.ZERO;
        Long nextFundingTs = null;
        if (fundingRates != null && fundingRates.containsKey(bestFutures.exchange)) {
            FundingRateData funding = fundingRates.get(bestFutures.exchange);
            fundingRate = funding.fundingRate();
            nextFundingTs = funding.nextFundingTs();
        }

        Map<ExchangeType, String> links = new EnumMap<>(ExchangeType.class);
        String[] parts = canonicalKey.split("/");
        CanonicalInstrument instrument = new CanonicalInstrument(parts[0], parts[1]);

        if (spotNativeSymbols != null && spotNativeSymbols.containsKey(bestSpot.exchange)) {
            String spotLink = ExchangeLinkResolver.spotLink(bestSpot.exchange,
                    spotNativeSymbols.get(bestSpot.exchange), instrument.quote());
            links.put(bestSpot.exchange, spotLink);
        }

        if (futuresNativeSymbols != null && futuresNativeSymbols.containsKey(bestFutures.exchange)) {
            String futuresLink = ExchangeLinkResolver.link(bestFutures.exchange,
                    futuresNativeSymbols.get(bestFutures.exchange), instrument.quote());
            links.put(bestFutures.exchange, futuresLink);
        }

        return new SpotFuturesArbitrageData(
                instrument,
                bestSpot.exchange,
                bestFutures.exchange,
                bestSpot.price,
                bestFutures.price,
                bestSpot.volume24h,
                bestFutures.volume24h,
                fundingRate,
                nextFundingTs,
                spread,
                links
        );
    }

    private ExchangePrice findBestSpotPrice(Map<ExchangeType, TickerData> prices,
                                           Set<ExchangeType> targetExchanges) {
        return prices.entrySet().stream()
                .filter(entry -> targetExchanges.contains(entry.getKey()))
                .map(entry -> {
                    ExchangeType exchange = entry.getKey();
                    TickerData ticker = entry.getValue();
                    return new ExchangePrice(
                            exchange,
                            ticker.lastPrice(),
                            ticker.volume24h()
                    );
                })
                .min(Comparator.comparing(ep -> ep.price))
                .orElse(null);
    }

    private ExchangePrice findBestFuturesPrice(Map<ExchangeType, TickerData> prices,
                                              Set<ExchangeType> targetExchanges) {
        return prices.entrySet().stream()
                .filter(entry -> targetExchanges.contains(entry.getKey()))
                .map(entry -> {
                    ExchangeType exchange = entry.getKey();
                    TickerData ticker = entry.getValue();
                    return new ExchangePrice(
                            exchange,
                            ticker.lastPrice(),
                            ticker.volume24h()
                    );
                })
                .max(Comparator.comparing(ep -> ep.price))
                .orElse(null);
    }

    private BigDecimal calculatePriceSpread(BigDecimal futuresPrice, BigDecimal spotPrice) {
        if (spotPrice.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO;
        return futuresPrice.subtract(spotPrice)
                .divide(spotPrice, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
    }

    private String extractCoinFromCanonicalKey(String canonicalKey) {
        if (canonicalKey == null || !canonicalKey.contains("/")) return null;
        return canonicalKey.split("/")[0];
    }

    private record ExchangePrice(ExchangeType exchange, BigDecimal price, BigDecimal volume24h) {
    }
}