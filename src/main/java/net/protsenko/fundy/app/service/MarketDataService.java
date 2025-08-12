package net.protsenko.fundy.app.service;

import lombok.extern.slf4j.Slf4j;
import net.protsenko.fundy.app.dto.rq.InstrumentsRequest;
import net.protsenko.fundy.app.dto.rq.TickerRequest;
import net.protsenko.fundy.app.dto.rq.TickersRequest;
import net.protsenko.fundy.app.dto.rs.InstrumentData;
import net.protsenko.fundy.app.dto.rs.TickerData;
import net.protsenko.fundy.app.exchange.ExchangeClient;
import net.protsenko.fundy.app.exchange.ExchangeClientFactory;
import net.protsenko.fundy.app.utils.SymbolNormalizer;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
public class MarketDataService extends BaseExchangeService {

    public MarketDataService(ExchangeClientFactory factory) {
        super(factory);
    }

    public List<InstrumentData> getAvailableInstruments(InstrumentsRequest req) {
        return across(req.effectiveExchanges(), c -> c.getInstruments().stream()).toList();
    }

    public List<TickerData> getTicker(TickerRequest req) {
        return req.effectiveExchanges().parallelStream()
                .map(ex -> {
                    try {
                        ExchangeClient c = client(ex);
                        var instruments = c.getInstruments();
                        var index = instruments.stream().collect(Collectors.toMap(
                                SymbolNormalizer::canonicalKey,
                                Function.identity(),
                                (a, b) -> a
                        ));
                        String key = (req.pair().base() + "/" + req.pair().quote()).toUpperCase(Locale.ROOT);
                        InstrumentData target = index.get(key);
                        if (target == null) return null;
                        return c.getTicker(target);
                    } catch (Exception e) {
                        log.warn("getTicker skip {}: {}", ex, e.getMessage());
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .toList();
    }

    public List<TickerData> getTickers(TickersRequest req) {
        return across(req.effectiveExchanges(), c -> {
            try {
                List<InstrumentData> instruments = c.getInstruments();
                if (instruments.isEmpty()) return Stream.empty();

                List<InstrumentData> target = instruments;
                if (req.hasPairs()) {
                    Map<String, InstrumentData> index = instruments.stream().collect(Collectors.toMap(
                            SymbolNormalizer::canonicalKey,
                            Function.identity(),
                            (a, b) -> a
                    ));
                    target = req.pairs().stream()
                            .map(p -> index.get((p.base() + "/" + p.quote()).toUpperCase(Locale.ROOT)))
                            .filter(Objects::nonNull)
                            .toList();
                }
                return c.getTickers(target).stream();
            } catch (Exception e) {
                log.warn("getTickers skip {}: {}", c.getExchangeType(), e.getMessage());
                return Stream.empty();
            }
        }).toList();
    }
}
