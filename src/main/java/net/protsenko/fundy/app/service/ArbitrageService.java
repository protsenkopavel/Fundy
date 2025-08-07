package net.protsenko.fundy.app.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.protsenko.fundy.app.dto.ArbitrageData;
import net.protsenko.fundy.app.dto.FundingRateData;
import net.protsenko.fundy.app.dto.InstrumentType;
import net.protsenko.fundy.app.dto.TradingInstrument;
import net.protsenko.fundy.app.exchange.ExchangeClient;
import net.protsenko.fundy.app.exchange.ExchangeClientFactory;
import net.protsenko.fundy.app.exchange.ExchangeType;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ArbitrageService {

    private static final MathContext MC = new MathContext(8, RoundingMode.HALF_UP);
    private final ExchangeClientFactory factory;

    public List<ArbitrageData> findAll() {
        Map<String, Agg> map = new ConcurrentHashMap<>();

        Arrays.stream(ExchangeType.values()).parallel().forEach(ex -> {
            ExchangeClient client = factory.getClient(ex);

            Map<String, BigDecimal> frMap = client.getAllFundingRates().stream()
                    .collect(Collectors.toMap(
                            fr -> fr.instrument().baseAsset(),
                            FundingRateData::fundingRate,
                            (a, b) -> b));

            List<TradingInstrument> instruments = client.getAvailableInstruments().stream()
                    .filter(i -> i.type() == InstrumentType.PERPETUAL)
                    .toList();

            if (instruments.isEmpty()) return;

            client.getTickers(instruments).forEach(t -> {
                String coin = t.instrument().baseAsset();
                Agg agg = map.computeIfAbsent(coin, c -> new Agg());
                agg.prices.put(ex, t.lastPrice());
                agg.fundings.put(ex, frMap.getOrDefault(coin, BigDecimal.ZERO));
            });
        });

        return map.entrySet().stream()
                .filter(e -> e.getValue().prices.size() >= 2)
                .map(e -> build(e.getKey(), e.getValue()))
                .sorted(Comparator.comparing(ArbitrageData::fundingSpread).reversed())
                .toList();
    }

    private ArbitrageData build(String coin, Agg agg) {
        Map.Entry<ExchangeType, BigDecimal> maxP = agg.prices.entrySet().stream()
                .max(Map.Entry.comparingByValue()).orElseThrow();
        Map.Entry<ExchangeType, BigDecimal> minP = agg.prices.entrySet().stream()
                .min(Map.Entry.comparingByValue()).orElseThrow();
        BigDecimal priceSpread = maxP.getValue().subtract(minP.getValue(), MC).divide(minP.getValue(), MC);

        Map.Entry<ExchangeType, BigDecimal> maxFr = agg.fundings.entrySet().stream()
                .max(Map.Entry.comparingByValue()).orElseThrow();
        Map.Entry<ExchangeType, BigDecimal> minFr = agg.fundings.entrySet().stream()
                .min(Map.Entry.comparingByValue()).orElseThrow();
        BigDecimal fundingSpread = maxFr.getValue().subtract(minFr.getValue(), MC);

        return new ArbitrageData(
                coin,
                Map.copyOf(agg.prices),
                Map.copyOf(agg.fundings),
                priceSpread,
                fundingSpread,
                new ArbitrageData.ArbitrageDecision(minFr.getKey(), maxFr.getKey())
        );
    }

    private static class Agg {
        final Map<ExchangeType, BigDecimal> prices = new ConcurrentHashMap<>();
        final Map<ExchangeType, BigDecimal> fundings = new ConcurrentHashMap<>();
    }
}
