package net.protsenko.fundy.app.service;

import lombok.extern.slf4j.Slf4j;
import net.protsenko.fundy.app.dto.InstrumentType;
import net.protsenko.fundy.app.dto.rq.InstrumentsRequest;
import net.protsenko.fundy.app.dto.rq.TickersRequest;
import net.protsenko.fundy.app.dto.rs.InstrumentData;
import net.protsenko.fundy.app.dto.rs.TickerData;
import net.protsenko.fundy.app.dto.rs.UniverseEntry;
import net.protsenko.fundy.app.exchange.ExchangeClientFactory;
import net.protsenko.fundy.app.exchange.ExchangeType;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Stream;

@Slf4j
@Service
public class MarketDataService extends BaseExchangeService {

    private final UniverseService universeService;

    public MarketDataService(ExchangeClientFactory factory, UniverseService universeService) {
        super(factory);
        this.universeService = universeService;
    }

    public List<UniverseEntry> getPerpUniverse(InstrumentsRequest req) {
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

    public List<TickerData> getTickers(TickersRequest req) {
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
                return c.getTickers(targets).stream();
            } catch (Exception e) {
                log.warn("getTickers skip {}: {}", c.getExchangeType(), e.getMessage());
                return Stream.empty();
            }
        }).toList();
    }
}
