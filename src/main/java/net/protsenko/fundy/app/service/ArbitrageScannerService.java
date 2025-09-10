package net.protsenko.fundy.app.service;

import lombok.extern.slf4j.Slf4j;
import net.protsenko.fundy.app.dto.*;
import net.protsenko.fundy.app.dto.rq.ArbitrageFilterRequest;
import net.protsenko.fundy.app.dto.rs.ArbitrageData;
import net.protsenko.fundy.app.dto.rs.FundingRateData;
import net.protsenko.fundy.app.dto.rs.InstrumentData;
import net.protsenko.fundy.app.dto.rs.TickerData;
import net.protsenko.fundy.app.exchange.ExchangeClientFactory;
import net.protsenko.fundy.app.exchange.ExchangeType;
import net.protsenko.fundy.app.utils.ExchangeLinkResolver;
import net.protsenko.fundy.app.utils.SymbolNormalizer;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
public class ArbitrageScannerService extends BaseExchangeService {

    private final UniverseService universeService;

    public ArbitrageScannerService(ExchangeClientFactory factory, UniverseService universeService) {
        super(factory);
        this.universeService = universeService;
    }

    public List<ArbitrageData> getArbitrageOpportunities(ArbitrageFilterRequest req) {
        Set<ExchangeType> exchanges = req.effectiveExchanges();
        Map<String, Map<ExchangeType, String>> perpUniverse = universeService.perpUniverse(exchanges);
        Map<String, Map<ExchangeType, TickerData>> priceData = collectPriceData(exchanges, perpUniverse);
        Map<String, Map<ExchangeType, FundingRateData>> fundingData = collectFundingData(exchanges, perpUniverse);
        List<ArbitrageData> opportunities = new ArrayList<>();

        for (String canonicalKey : perpUniverse.keySet()) {
            Map<ExchangeType, TickerData> instrumentPrices = priceData.get(canonicalKey);
            Map<ExchangeType, FundingRateData> instrumentFunding = fundingData.get(canonicalKey);

            if (instrumentPrices == null || instrumentPrices.size() < 2) {
                continue;
            }

            ArbitrageData opportunity = analyzeArbitrageOpportunity(
                    canonicalKey, instrumentPrices, instrumentFunding, req
            );

            if (opportunity != null) {
                opportunities.add(opportunity);
            }
        }

        return opportunities.stream()
                .sorted((a, b) -> {
                    BigDecimal spreadA = calculateCombinedSpread(a);
                    BigDecimal spreadB = calculateCombinedSpread(b);
                    return spreadB.compareTo(spreadA);
                })
                .collect(Collectors.toList());
    }

    private Map<String, Map<ExchangeType, TickerData>> collectPriceData(
            Set<ExchangeType> exchanges, Map<String, Map<ExchangeType, String>> universe) {

        Map<String, Map<ExchangeType, TickerData>> result = new ConcurrentHashMap<>();

        across(exchanges, client -> {
            try {
                ExchangeType ex = client.getExchangeType();
                List<InstrumentData> targets = universe.entrySet().stream()
                        .filter(e -> e.getValue().containsKey(ex))
                        .map(e -> {
                            String[] parts = e.getKey().split("/");
                            String base = parts.length > 0 ? parts[0] : "";
                            String quote = parts.length > 1 ? parts[1] : "USDT";
                            return new InstrumentData(base, quote, InstrumentType.PERPETUAL,
                                    e.getValue().get(ex), ex);
                        })
                        .collect(Collectors.toList());

                if (targets.isEmpty()) return Stream.empty();

                return client.getFuturesTickers(targets).stream()
                        .map(ticker -> {
                            String canonicalKey = SymbolNormalizer.canonicalKey(ticker.instrument());
                            result.computeIfAbsent(canonicalKey, k -> new EnumMap<>(ExchangeType.class))
                                    .put(ex, ticker);
                            return ticker;
                        });
            } catch (Exception e) {
                log.warn("Failed to get price data from {}: {}", client.getExchangeType(), e.getMessage());
                return Stream.empty();
            }
        }).count();

        return result;
    }

    private Map<String, Map<ExchangeType, FundingRateData>> collectFundingData(
            Set<ExchangeType> exchanges, Map<String, Map<ExchangeType, String>> universe) {

        Map<String, Map<ExchangeType, FundingRateData>> result = new ConcurrentHashMap<>();

        across(exchanges, client -> {
            try {
                ExchangeType ex = client.getExchangeType();
                List<InstrumentData> targets = universe.entrySet().stream()
                        .filter(e -> e.getValue().containsKey(ex))
                        .map(e -> {
                            String[] parts = e.getKey().split("/");
                            String base = parts.length > 0 ? parts[0] : "";
                            String quote = parts.length > 1 ? parts[1] : "USDT";
                            return new InstrumentData(base, quote, InstrumentType.PERPETUAL,
                                    e.getValue().get(ex), ex);
                        })
                        .collect(Collectors.toList());

                if (targets.isEmpty()) return Stream.empty();

                return client.getFundingRates(targets).stream()
                        .map(funding -> {
                            String canonicalKey = SymbolNormalizer.canonicalKey(funding.instrument());
                            result.computeIfAbsent(canonicalKey, k -> new EnumMap<>(ExchangeType.class))
                                    .put(ex, funding);
                            return funding;
                        });
            } catch (Exception e) {
                log.warn("Failed to get funding data from {}: {}", client.getExchangeType(), e.getMessage());
                return Stream.empty();
            }
        }).count();

        return result;
    }

    private ArbitrageData analyzeArbitrageOpportunity(
            String canonicalKey,
            Map<ExchangeType, TickerData> prices,
            Map<ExchangeType, FundingRateData> fundingRates,
            ArbitrageFilterRequest req) {

        if (prices.size() < 2) return null;

        String[] parts = canonicalKey.split("/");
        if (parts.length < 2) return null;
        CanonicalInstrument instrument = new CanonicalInstrument(parts[0], parts[1]);

        List<PriceSpread> priceSpreads = calculatePriceSpreads(prices);

        List<FundingSpread> fundingSpreads = calculateFundingSpreads(fundingRates);

        ArbitrageOpportunity bestOpportunity = findBestArbitrageOpportunity(
                priceSpreads, fundingSpreads, req);

        if (bestOpportunity == null) return null;

        Map<ExchangeType, BigDecimal> priceMap = prices.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> e.getValue().lastPrice()
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
            if (!hasSameAccrualTime(nextFundingTsMap)) {
                return null;
            }
        }

        Map<ExchangeType, String> links = generateTradingLinks(instrument, prices.keySet());

        return new ArbitrageData(
                instrument,
                priceMap,
                fundingMap,
                nextFundingTsMap,
                bestOpportunity.priceSpread(),
                bestOpportunity.fundingSpread(),
                new ArbitrageData.Decision(bestOpportunity.longExchange(), bestOpportunity.shortExchange()),
                links
        );
    }

    private List<PriceSpread> calculatePriceSpreads(Map<ExchangeType, TickerData> prices) {
        List<PriceSpread> spreads = new ArrayList<>();
        List<ExchangeType> exchanges = new ArrayList<>(prices.keySet());

        for (int i = 0; i < exchanges.size(); i++) {
            for (int j = i + 1; j < exchanges.size(); j++) {
                ExchangeType ex1 = exchanges.get(i);
                ExchangeType ex2 = exchanges.get(j);

                BigDecimal price1 = prices.get(ex1).lastPrice();
                BigDecimal price2 = prices.get(ex2).lastPrice();

                if (price1 != null && price2 != null && price1.compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal spread = price1.subtract(price2).divide(price1, 6, RoundingMode.HALF_UP);
                    spreads.add(new PriceSpread(ex1, ex2, spread));
                }
            }
        }

        return spreads;
    }

    private List<FundingSpread> calculateFundingSpreads(Map<ExchangeType, FundingRateData> fundingRates) {
        if (fundingRates == null || fundingRates.size() < 2) return new ArrayList<>();

        List<FundingSpread> spreads = new ArrayList<>();
        List<ExchangeType> exchanges = new ArrayList<>(fundingRates.keySet());

        for (int i = 0; i < exchanges.size(); i++) {
            for (int j = i + 1; j < exchanges.size(); j++) {
                ExchangeType ex1 = exchanges.get(i);
                ExchangeType ex2 = exchanges.get(j);

                BigDecimal rate1 = fundingRates.get(ex1).fundingRate();
                BigDecimal rate2 = fundingRates.get(ex2).fundingRate();

                if (rate1 != null && rate2 != null) {
                    BigDecimal spread = rate2.subtract(rate1);
                    spreads.add(new FundingSpread(ex1, ex2, spread));
                }
            }
        }

        return spreads;
    }

    private ArbitrageOpportunity findBestArbitrageOpportunity(
            List<PriceSpread> priceSpreads,
            List<FundingSpread> fundingSpreads,
            ArbitrageFilterRequest req) {

        ArbitrageOpportunity best = null;
        BigDecimal bestScore = BigDecimal.ZERO;

        for (PriceSpread priceSpread : priceSpreads) {
            BigDecimal priceArbSpread = priceSpread.spread().abs();
            if (priceArbSpread.compareTo(req.minPr()) >= 0) {
                ExchangeType longEx = priceSpread.spread().compareTo(BigDecimal.ZERO) > 0 ?
                        priceSpread.ex2() : priceSpread.ex1();
                ExchangeType shortEx = priceSpread.spread().compareTo(BigDecimal.ZERO) > 0 ?
                        priceSpread.ex1() : priceSpread.ex2();

                if (priceArbSpread.compareTo(bestScore) > 0) {
                    best = new ArbitrageOpportunity(
                            longEx, shortEx, priceArbSpread, BigDecimal.ZERO, ArbitrageType.PRICE
                    );
                    bestScore = priceArbSpread;
                }
            }
        }

        for (FundingSpread fundingSpread : fundingSpreads) {
            BigDecimal fundingArbSpread = fundingSpread.spread().abs();
            if (fundingArbSpread.compareTo(req.minFr()) >= 0) {
                ExchangeType longEx = fundingSpread.spread().compareTo(BigDecimal.ZERO) > 0 ?
                        fundingSpread.ex1() : fundingSpread.ex2();
                ExchangeType shortEx = fundingSpread.spread().compareTo(BigDecimal.ZERO) > 0 ?
                        fundingSpread.ex2() : fundingSpread.ex1();

                BigDecimal score = fundingArbSpread.multiply(BigDecimal.valueOf(8760));
                if (score.compareTo(bestScore) > 0) {
                    best = new ArbitrageOpportunity(
                            longEx, shortEx, BigDecimal.ZERO, fundingArbSpread, ArbitrageType.FUNDING
                    );
                    bestScore = score;
                }
            }
        }

        for (PriceSpread priceSpread : priceSpreads) {
            for (FundingSpread fundingSpread : fundingSpreads) {
                if (!isSameExchangePair(priceSpread, fundingSpread)) continue;

                BigDecimal priceArbSpread = priceSpread.spread().abs();
                BigDecimal fundingArbSpread = fundingSpread.spread().abs();

                if (priceArbSpread.compareTo(req.minPr()) < 0 ||
                        fundingArbSpread.compareTo(req.minFr()) < 0) continue;

                ArbitrageCandidate direction1 = evaluateDirection(
                        priceSpread.ex1(), priceSpread.ex2(), priceSpread, fundingSpread);
                ArbitrageCandidate direction2 = evaluateDirection(
                        priceSpread.ex2(), priceSpread.ex1(), priceSpread, fundingSpread);

                ArbitrageCandidate betterDirection = direction1.score().compareTo(direction2.score()) > 0 ?
                        direction1 : direction2;

                if (betterDirection.score().compareTo(bestScore) > 0) {
                    best = new ArbitrageOpportunity(
                            betterDirection.longEx(), betterDirection.shortEx(),
                            betterDirection.priceSpread(), betterDirection.fundingSpread(),
                            ArbitrageType.COMBINED
                    );
                    bestScore = betterDirection.score();
                }
            }
        }

        return best;
    }

    private ArbitrageCandidate evaluateDirection(
            ExchangeType longEx, ExchangeType shortEx,
            PriceSpread priceSpread, FundingSpread fundingSpread) {

        BigDecimal priceArbSpread = calculatePriceSpreadForDirection(longEx, shortEx, priceSpread);
        BigDecimal fundingArbSpread = calculateFundingSpreadForDirection(longEx, shortEx, fundingSpread);

        BigDecimal annualizedFunding = fundingArbSpread.multiply(BigDecimal.valueOf(8760));
        BigDecimal score = priceArbSpread.add(annualizedFunding);

        return new ArbitrageCandidate(
                longEx, shortEx, priceArbSpread, fundingArbSpread, score, ArbitrageType.COMBINED
        );
    }

    private BigDecimal calculatePriceSpreadForDirection(
            ExchangeType longEx, ExchangeType shortEx, PriceSpread priceSpread) {

        ExchangeType higherPriceEx, lowerPriceEx;
        if (priceSpread.spread().compareTo(BigDecimal.ZERO) > 0) {
            higherPriceEx = priceSpread.ex1();
            lowerPriceEx = priceSpread.ex2();
        } else {
            higherPriceEx = priceSpread.ex2();
            lowerPriceEx = priceSpread.ex1();
        }

        if (shortEx.equals(higherPriceEx) && longEx.equals(lowerPriceEx)) {
            return priceSpread.spread().abs();
        }

        return priceSpread.spread().abs().negate();
    }

    private BigDecimal calculateFundingSpreadForDirection(
            ExchangeType longEx, ExchangeType shortEx, FundingSpread fundingSpread) {

        BigDecimal rate1, rate2;

        if (fundingSpread.ex1().equals(fundingSpread.ex1())) {
            rate1 = BigDecimal.ZERO;
            rate2 = fundingSpread.spread();
        } else {
            rate1 = fundingSpread.spread().negate();
            rate2 = BigDecimal.ZERO;
        }

        BigDecimal longFundingRate = longEx.equals(fundingSpread.ex1()) ? rate1 : rate2;
        BigDecimal shortFundingRate = shortEx.equals(fundingSpread.ex1()) ? rate1 : rate2;

        BigDecimal longFundingPayment, shortFundingPayment;

        if (longFundingRate.compareTo(BigDecimal.ZERO) > 0) {
            longFundingPayment = longFundingRate.negate();
        } else {
            longFundingPayment = longFundingRate.negate();
        }

        if (shortFundingRate.compareTo(BigDecimal.ZERO) > 0) {
            shortFundingPayment = shortFundingRate;
        } else {
            shortFundingPayment = shortFundingRate;
        }

        return longFundingPayment.add(shortFundingPayment);
    }

    private boolean isSameExchangePair(PriceSpread priceSpread, FundingSpread fundingSpread) {
        return (priceSpread.ex1().equals(fundingSpread.ex1()) && priceSpread.ex2().equals(fundingSpread.ex2())) ||
                (priceSpread.ex1().equals(fundingSpread.ex2()) && priceSpread.ex2().equals(fundingSpread.ex1()));
    }

    private BigDecimal calculateCombinedSpread(ArbitrageData data) {
        return data.priceSpread().abs().add(data.fundingSpread().abs());
    }

    private Map<ExchangeType, String> generateTradingLinks(CanonicalInstrument instrument, Set<ExchangeType> exchanges) {
        Map<ExchangeType, String> links = new EnumMap<>(ExchangeType.class);

        for (ExchangeType exchange : exchanges) {
            InstrumentData instrumentData = new InstrumentData(
                    instrument.base(),
                    instrument.quote(),
                    InstrumentType.PERPETUAL,
                    instrument.base() + instrument.quote(),
                    exchange
            );
            String link = ExchangeLinkResolver.link(exchange, instrumentData);
            links.put(exchange, link);
        }

        return links;
    }

    private boolean hasSameAccrualTime(Map<ExchangeType, Long> nextFundingTsMap) {
        if (nextFundingTsMap == null || nextFundingTsMap.isEmpty()) {
            return false;
        }

        Long firstTs = null;
        for (Long ts : nextFundingTsMap.values()) {
            if (ts == null) continue;
            if (firstTs == null) {
                firstTs = ts;
            } else if (!firstTs.equals(ts)) {
                return false;
            }
        }

        return firstTs != null;
    }

}