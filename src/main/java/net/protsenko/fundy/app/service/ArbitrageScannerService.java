package net.protsenko.fundy.app.service;

import lombok.extern.slf4j.Slf4j;
import net.protsenko.fundy.app.dto.*;
import net.protsenko.fundy.app.dto.rq.ArbitrageFilterRequest;
import net.protsenko.fundy.app.dto.rs.ArbitrageData;
import net.protsenko.fundy.app.dto.rs.FundingRateData;
import net.protsenko.fundy.app.dto.rs.InstrumentData;
import net.protsenko.fundy.app.dto.rs.TickerData;
import net.protsenko.fundy.app.exchange.ExchangeClient;
import net.protsenko.fundy.app.exchange.ExchangeClientFactory;
import net.protsenko.fundy.app.exchange.ExchangeType;
import net.protsenko.fundy.app.utils.ExchangeLinkResolver;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
public class ArbitrageScannerService extends BaseExchangeService {

    private final UniverseService universeService;

    public ArbitrageScannerService(ExchangeClientFactory factory,
                                   UniverseService universeService) {
        super(factory);
        this.universeService = universeService;
    }

    public List<ArbitrageData> getArbitrageOpportunities(ArbitrageFilterRequest req) {
        Set<ExchangeType> exchanges = req.effectiveExchanges();
        Map<String, Map<ExchangeType, String>> uni = universeService.perpUniverse(exchanges);

        Map<String, InstrumentArbitrageData> instrumentData = collectInstrumentData(exchanges, uni);

        return instrumentData.values().stream()
                .map(this::analyzeArbitrageOpportunity)
                .filter(Objects::nonNull)
                .filter(data -> passesFilters(data, req))
                .sorted(this::compareArbitrageData)
                .toList();
    }

    private Map<String, InstrumentArbitrageData> collectInstrumentData(Set<ExchangeType> exchanges,
                                                                       Map<String, Map<ExchangeType, String>> uni) {
        Map<String, InstrumentArbitrageData> result = new HashMap<>();

        across(exchanges, client -> loadExchangeData(client, uni)).forEach(data -> {
            result.computeIfAbsent(data.canonicalKey(), k -> new InstrumentArbitrageData(data.canonicalKey()))
                    .addExchangeData(data);
        });

        return result;
    }

    private Stream<ExchangeArbitrageData> loadExchangeData(ExchangeClient client,
                                                           Map<String, Map<ExchangeType, String>> uni) {
        try {
            ExchangeType ex = client.getExchangeType();

            List<InstrumentData> instruments = uni.entrySet().stream()
                    .map(e -> {
                        String nativeSymbol = e.getValue().get(ex);
                        return nativeSymbol == null ? null : makeInstr(e.getKey(), nativeSymbol, ex);
                    })
                    .filter(Objects::nonNull)
                    .toList();

            if (instruments.isEmpty()) {
                return Stream.empty();
            }

            Map<String, TickerData> tickers = client.getFuturesTickers(instruments).stream()
                    .collect(Collectors.toMap(t -> t.instrument().nativeSymbol(), Function.identity()));

            Map<String, FundingRateData> fundingRates = client.getFundingRates(instruments).stream()
                    .collect(Collectors.toMap(fr -> fr.instrument().nativeSymbol(), Function.identity()));

            return instruments.stream()
                    .filter(instr -> tickers.containsKey(instr.nativeSymbol()) &&
                            fundingRates.containsKey(instr.nativeSymbol()))
                    .map(instr -> {
                        TickerData ticker = tickers.get(instr.nativeSymbol());
                        FundingRateData funding = fundingRates.get(instr.nativeSymbol());
                        String canonicalKey = instr.baseAsset() + "/" + instr.quoteAsset();
                        return new ExchangeArbitrageData(
                                canonicalKey,
                                ex,
                                ticker.lastPrice(),
                                funding.fundingRate(),
                                funding.nextFundingTs(),
                                ExchangeLinkResolver.link(ex, instr)
                        );
                    });

        } catch (Exception e) {
            log.warn("Skip {}: {}", client.getExchangeType(), e.getMessage());
            return Stream.empty();
        }
    }

    private InstrumentData makeInstr(String canonicalKey, String nativeSymbol, ExchangeType ex) {
        String[] p = canonicalKey.split("/");
        String base = p.length > 0 ? p[0] : "";
        String quote = p.length > 1 ? p[1] : "USDT";
        return new InstrumentData(base, quote, InstrumentType.PERPETUAL, nativeSymbol, ex);
    }

    private ArbitrageData analyzeArbitrageOpportunity(InstrumentArbitrageData data) {
        if (data.exchangeData.size() < 2) {
            return null;
        }

        PriceSpreadAnalysis priceAnalysis = analyzePriceSpread(data);

        FundingSpreadAnalysis fundingAnalysis = analyzeFundingSpread(data);

        ArbitrageType arbitrageType = determineArbitrageType(priceAnalysis, fundingAnalysis);
        if (arbitrageType == ArbitrageType.NONE) {
            return null;
        }

        ArbitrageData.Decision decision = makeDecision(arbitrageType, priceAnalysis, fundingAnalysis);

        BigDecimal priceSpread = calculatePriceSpread(priceAnalysis);
        BigDecimal fundingSpread = calculateFundingSpread(fundingAnalysis);

        Map<ExchangeType, String> links = data.exchangeData.values().stream()
                .collect(Collectors.toMap(ExchangeArbitrageData::exchange, ExchangeArbitrageData::link));

        return new ArbitrageData(
                data.getCanonicalInstrument(),
                data.getPrices(),
                data.getFundingRates(),
                data.getNextFundingTs(),
                priceSpread,
                fundingSpread,
                decision,
                links
        );
    }

    private PriceSpreadAnalysis analyzePriceSpread(InstrumentArbitrageData data) {
        List<ExchangeArbitrageData> sortedByPrice = data.exchangeData.values().stream()
                .sorted(Comparator.comparing(ExchangeArbitrageData::price))
                .toList();

        if (sortedByPrice.size() < 2) return null;

        ExchangeArbitrageData minPrice = sortedByPrice.getFirst();
        ExchangeArbitrageData maxPrice = sortedByPrice.getLast();

        BigDecimal spread = maxPrice.price().subtract(minPrice.price())
                .divide(minPrice.price(), 6, RoundingMode.HALF_UP);

        return new PriceSpreadAnalysis(minPrice, maxPrice, spread);
    }

    private FundingSpreadAnalysis analyzeFundingSpread(InstrumentArbitrageData data) {
        List<ExchangeArbitrageData> sortedByFunding = data.exchangeData.values().stream()
                .sorted(Comparator.comparing(ExchangeArbitrageData::fundingRate))
                .toList();

        if (sortedByFunding.size() < 2) return null;

        ExchangeArbitrageData minFunding = sortedByFunding.getFirst();
        ExchangeArbitrageData maxFunding = sortedByFunding.getLast();

        BigDecimal spread = maxFunding.fundingRate().subtract(minFunding.fundingRate());

        return new FundingSpreadAnalysis(minFunding, maxFunding, spread);
    }

    private ArbitrageType determineArbitrageType(PriceSpreadAnalysis priceAnalysis,
                                                 FundingSpreadAnalysis fundingAnalysis) {
        boolean hasPriceSpread = priceAnalysis != null && priceAnalysis.spread().abs().compareTo(BigDecimal.valueOf(0.001)) > 0;
        boolean hasFundingSpread = fundingAnalysis != null && fundingAnalysis.spread().abs().compareTo(BigDecimal.valueOf(0.0001)) > 0;

        if (hasPriceSpread && hasFundingSpread) {
            if (priceAnalysis.minPriceEx().fundingRate().compareTo(BigDecimal.ZERO) < 0 &&
                    priceAnalysis.maxPriceEx().fundingRate().compareTo(BigDecimal.ZERO) > 0) {
                return ArbitrageType.COMBINED;
            }
        }

        if (hasPriceSpread) {
            return ArbitrageType.PRICE;
        }

        if (hasFundingSpread) {
            return ArbitrageType.FUNDING;
        }

        return ArbitrageType.NONE;
    }

    private ArbitrageData.Decision makeDecision(ArbitrageType type,
                                                PriceSpreadAnalysis priceAnalysis,
                                                FundingSpreadAnalysis fundingAnalysis) {
        switch (type) {
            case PRICE:
                if (priceAnalysis != null) {
                    return new ArbitrageData.Decision(
                            priceAnalysis.minPriceEx().exchange(),
                            priceAnalysis.maxPriceEx().exchange()
                    );
                }
                break;
            case FUNDING:
                if (fundingAnalysis != null) {
                    return new ArbitrageData.Decision(
                            fundingAnalysis.minFundingEx().exchange(),
                            fundingAnalysis.maxFundingEx().exchange()
                    );
                }
                break;
            case COMBINED:
                if (priceAnalysis != null) {
                    return new ArbitrageData.Decision(
                            priceAnalysis.minPriceEx().exchange(),
                            priceAnalysis.maxPriceEx().exchange()
                    );
                }
                break;
        }
        return new ArbitrageData.Decision(null, null);
    }

    private BigDecimal calculatePriceSpread(PriceSpreadAnalysis analysis) {
        return analysis != null ? analysis.spread() : BigDecimal.ZERO;
    }

    private BigDecimal calculateFundingSpread(FundingSpreadAnalysis analysis) {
        return analysis != null ? analysis.spread() : BigDecimal.ZERO;
    }

    private boolean passesFilters(ArbitrageData data, ArbitrageFilterRequest req) {
        if (data.fundingRates().values().stream()
                .anyMatch(fr -> fr.abs().compareTo(req.minFr()) < 0)) {
            return false;
        }

        return data.prices().values().stream()
                .noneMatch(price -> price.compareTo(req.minPr()) < 0);
    }

    private int compareArbitrageData(ArbitrageData a, ArbitrageData b) {
        BigDecimal aSpread = a.priceSpread().abs().add(a.fundingSpread().abs());
        BigDecimal bSpread = b.priceSpread().abs().add(b.fundingSpread().abs());
        return bSpread.compareTo(aSpread);
    }

    private static class InstrumentArbitrageData {
        private final String canonicalKey;
        private final Map<ExchangeType, ExchangeArbitrageData> exchangeData = new EnumMap<>(ExchangeType.class);

        public InstrumentArbitrageData(String canonicalKey) {
            this.canonicalKey = canonicalKey;
        }

        public void addExchangeData(ExchangeArbitrageData data) {
            exchangeData.put(data.exchange(), data);
        }

        public CanonicalInstrument getCanonicalInstrument() {
            String[] parts = canonicalKey.split("/");
            return new CanonicalInstrument(parts[0], parts.length > 1 ? parts[1] : "USDT");
        }

        public Map<ExchangeType, BigDecimal> getPrices() {
            return exchangeData.values().stream()
                    .collect(Collectors.toMap(ExchangeArbitrageData::exchange, ExchangeArbitrageData::price));
        }

        public Map<ExchangeType, BigDecimal> getFundingRates() {
            return exchangeData.values().stream()
                    .collect(Collectors.toMap(ExchangeArbitrageData::exchange, ExchangeArbitrageData::fundingRate));
        }

        public Map<ExchangeType, Long> getNextFundingTs() {
            return exchangeData.values().stream()
                    .collect(Collectors.toMap(ExchangeArbitrageData::exchange, ExchangeArbitrageData::nextFundingTs));
        }
    }
}
