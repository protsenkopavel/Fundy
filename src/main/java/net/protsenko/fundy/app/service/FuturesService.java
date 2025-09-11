package net.protsenko.fundy.app.service;

import lombok.extern.slf4j.Slf4j;
import net.protsenko.fundy.app.domain.InstrumentType;
import net.protsenko.fundy.app.dto.rq.InstrumentsRequest;
import net.protsenko.fundy.app.dto.rq.TickersRequest;
import net.protsenko.fundy.app.dto.rs.InstrumentData;
import net.protsenko.fundy.app.dto.rs.TickerData;
import net.protsenko.fundy.app.dto.rs.UniverseEntry;
import net.protsenko.fundy.app.exchange.ExchangeClientFactory;
import net.protsenko.fundy.app.exchange.ExchangeType;
import net.protsenko.fundy.app.utils.SymbolNormalizer;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
public class FuturesService extends BaseExchangeService {

    private final UniverseService universeService;

    public FuturesService(ExchangeClientFactory factory, UniverseService universeService) {
        super(factory);
        this.universeService = universeService;
    }

    public List<UniverseEntry> getFuturesUniverse(InstrumentsRequest req) {
        Map<String, Map<ExchangeType, String>> uni = universeService.perpUniverse(req.effectiveExchanges());
        return uni.entrySet().stream()
                .map(e -> {
                    String[] p = e.getKey().split("/");
                    String base = p.length > 0 ? p[0] : "";
                    String quote = p.length > 1 ? p[1] : "USDT";
                    return new UniverseEntry(base, quote, Map.copyOf(e.getValue()));
                })
                .sorted(Comparator.comparing(UniverseEntry::token))
                .toList();
    }

    public List<TickerData> getFuturesTickers(TickersRequest req) {
        Map<String, Map<ExchangeType, String>> uni = universeService.perpUniverse(req.effectiveExchanges());

        return across(req.effectiveExchanges(), c -> {
            try {
                ExchangeType ex = c.getExchangeType();

                List<InstrumentData> targets;
                if (req.hasPairs()) {
                    targets = req.pairs().stream()
                            .map(p -> {
                                String k = (p.base() + "/" + p.quote()).toUpperCase(Locale.ROOT);
                                String nativeSymbol = uni.getOrDefault(k, Map.of()).get(ex);
                                if (nativeSymbol == null) return null;
                                return new InstrumentData(
                                        p.base().toUpperCase(Locale.ROOT),
                                        p.quote().toUpperCase(Locale.ROOT),
                                        InstrumentType.PERPETUAL,
                                        nativeSymbol,
                                        ex
                                );
                            })
                            .filter(Objects::nonNull)
                            .toList();
                } else {
                    targets = uni.entrySet().stream()
                            .map(e -> {
                                String nativeSymbol = e.getValue().get(ex);
                                if (nativeSymbol == null) return null;
                                String[] p = e.getKey().split("/");
                                String base = p.length > 0 ? p[0] : "";
                                String quote = p.length > 1 ? p[1] : "USDT";
                                return new InstrumentData(base, quote,
                                        InstrumentType.PERPETUAL,
                                        nativeSymbol, ex);
                            })
                            .filter(Objects::nonNull)
                            .toList();
                }

                if (targets.isEmpty()) return Stream.empty();
                return c.getFuturesTickers(targets).stream();
            } catch (Exception e) {
                log.warn("getFuturesTickers skip {}: {}", c.getExchangeType(), e.getMessage());
                return Stream.empty();
            }
        }).toList();
    }

    public Map<String, Map<ExchangeType, TickerData>> collectFuturesPriceData(Set<ExchangeType> exchanges) {
        Map<String, Map<ExchangeType, String>> universe = universeService.perpUniverse(exchanges);
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
                log.warn("Failed to get futures price data from {}: {}", client.getExchangeType(), e.getMessage());
                return Stream.empty();
            }
        }).count();

        return result;
    }
}