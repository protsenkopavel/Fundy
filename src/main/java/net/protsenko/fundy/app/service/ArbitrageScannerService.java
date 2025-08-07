package net.protsenko.fundy.app.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.protsenko.fundy.app.dto.BucketEntry;
import net.protsenko.fundy.app.dto.InstrumentType;
import net.protsenko.fundy.app.dto.rq.ArbitrageFilterRequest;
import net.protsenko.fundy.app.dto.rs.ArbitrageData;
import net.protsenko.fundy.app.dto.rs.FundingRateData;
import net.protsenko.fundy.app.dto.rs.InstrumentData;
import net.protsenko.fundy.app.dto.rs.TickerData;
import net.protsenko.fundy.app.exchange.ExchangeClient;
import net.protsenko.fundy.app.exchange.ExchangeClientFactory;
import net.protsenko.fundy.app.exchange.ExchangeType;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class ArbitrageScannerService {
    private static final MathContext MC = new MathContext(8, RoundingMode.HALF_UP);
    private final ExchangeClientFactory factory;

    public List<ArbitrageData> getArbitrageOpportunities(ArbitrageFilterRequest f) {

        ZoneId zone = f.zone();
        BigDecimal minFr = f.minFr();
        BigDecimal minPr = f.minPr();
        Set<ExchangeType> ex = f.effectiveExchanges();

        Map<String, List<BucketEntry>> bySymbol = ex.parallelStream()
                .flatMap(e -> loadExchangeData(e, zone))
                .collect(Collectors.groupingByConcurrent(BucketEntry::symbol));

        return bySymbol.entrySet().parallelStream()
                .map(this::buildView)
                .filter(Objects::nonNull)
                .filter(a -> a.fundingSpread().compareTo(minFr) >= 0
                        && a.priceSpread().compareTo(minPr) >= 0)
                .sorted(Comparator.comparing(ArbitrageData::fundingSpread).reversed())
                .toList();
    }

    private Stream<BucketEntry> loadExchangeData(ExchangeType ex, ZoneId zone) {
        try {
            ExchangeClient client = factory.getClient(ex);

            Map<String, FundingRateData> fundBySymbol = client.getAllFundingRates().stream()
                    .collect(Collectors.toMap(
                            fr -> key(fr.instrument()),
                            fr -> fr,
                            (a, b) -> a
                    ));

            List<InstrumentData> instruments = client.getAvailableInstruments().stream()
                    .filter(i -> i.type() == InstrumentType.PERPETUAL)
                    .toList();
            if (instruments.isEmpty()) return Stream.empty();

            List<TickerData> tickers = client.getTickers(instruments);

            return tickers.stream()
                    .map(tk -> {
                        String symbol = key(tk.instrument());
                        FundingRateData fr = fundBySymbol.get(symbol);

                        BigDecimal frValue = fr == null ? null : fr.fundingRate();
                        long nextFunding = fr == null ? 0L : Instant
                                .ofEpochMilli(fr.nextFundingTs())
                                .atZone(zone).toInstant().toEpochMilli();

                        return new BucketEntry(symbol, ex, tk.lastPrice(), frValue, nextFunding);
                    })
                    .filter(be -> be.price().compareTo(BigDecimal.ZERO) > 0);
        } catch (Exception e) {
            log.warn("Не удалось получить данные с биржи {}", ex, e);
            return Stream.empty();
        }
    }

    private ArbitrageData buildView(Map.Entry<String, List<BucketEntry>> e) {
        String symbol = e.getKey();
        List<BucketEntry> list = e.getValue();

        if (list.stream().map(BucketEntry::price).distinct().count() < 2) return null;
        if (list.stream().map(BucketEntry::funding).filter(Objects::nonNull).distinct().count() < 2) return null;

        BucketEntry maxPrice = list.stream().max(Comparator.comparing(BucketEntry::price)).orElseThrow();
        BucketEntry minPrice = list.stream().min(Comparator.comparing(BucketEntry::price)).orElseThrow();
        if (minPrice.price().compareTo(BigDecimal.ZERO) == 0) return null;

        BigDecimal priceSpread = maxPrice.price()
                .subtract(minPrice.price(), MC)
                .divide(minPrice.price(), MC);

        BucketEntry maxFr = list.stream().filter(b -> b.funding() != null)
                .max(Comparator.comparing(BucketEntry::funding)).orElseThrow();
        BucketEntry minFr = list.stream().filter(b -> b.funding() != null)
                .min(Comparator.comparing(BucketEntry::funding)).orElseThrow();
        BigDecimal fundingSpread = maxFr.funding().subtract(minFr.funding(), MC);

        ArbitrageData.Decision decision = pickBestPair(list);
        if (decision == null) return null;

        Map<ExchangeType, BigDecimal> priceMap = list.stream()
                .collect(Collectors.toMap(BucketEntry::ex, BucketEntry::price, (a, b) -> a));
        Map<ExchangeType, BigDecimal> frMap = list.stream()
                .filter(it -> it.funding() != null)
                .collect(Collectors.toMap(BucketEntry::ex, BucketEntry::funding, BigDecimal::max));
        Map<ExchangeType, Long> nextFundingMap = list.stream()
                .collect(Collectors.toMap(BucketEntry::ex, BucketEntry::nextFundingTs, Math::min));

        return new ArbitrageData(
                symbol,
                Map.copyOf(priceMap),
                Map.copyOf(frMap),
                Map.copyOf(nextFundingMap),
                priceSpread,
                fundingSpread,
                decision
        );
    }

    private ArbitrageData.Decision pickBestPair(List<BucketEntry> list) {
        BigDecimal bestScore = null;
        ExchangeType bestLong = null, bestShort = null;

        for (int i = 0; i < list.size(); i++) {
            for (int j = 0; j < list.size(); j++) {
                if (i == j) continue;

                BucketEntry L = list.get(i);
                BucketEntry S = list.get(j);

                if (L.funding() == null || S.funding() == null) continue;
                if (L.price().compareTo(S.price()) >= 0) continue;

                BigDecimal fundingProfit = S.funding().subtract(L.funding(), MC);
                BigDecimal priceProfit = S.price().subtract(L.price(), MC)
                        .divide(L.price(), MC);
                BigDecimal score = fundingProfit.add(priceProfit, MC);

                if (bestScore == null || score.compareTo(bestScore) > 0) {
                    bestScore = score;
                    bestLong = L.ex();
                    bestShort = S.ex();
                }
            }
        }
        return bestLong == null ? null : new ArbitrageData.Decision(bestLong, bestShort);
    }

    private String key(InstrumentData i) {
        return i.baseAsset() + i.quoteAsset();
    }
}
