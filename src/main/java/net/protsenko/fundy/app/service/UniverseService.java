package net.protsenko.fundy.app.service;

import net.protsenko.fundy.app.dto.InstrumentType;
import net.protsenko.fundy.app.dto.rs.InstrumentData;
import net.protsenko.fundy.app.exchange.ExchangeClient;
import net.protsenko.fundy.app.exchange.ExchangeClientFactory;
import net.protsenko.fundy.app.exchange.ExchangeType;
import net.protsenko.fundy.app.utils.SymbolNormalizer;
import net.protsenko.fundy.app.utils.UniverseNormalizer;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Stream;

@Service
public class UniverseService extends BaseExchangeService {

    public UniverseService(ExchangeClientFactory factory) {
        super(factory);
    }

    @Cacheable(cacheNames = "universe-perp-24h", key = "#exchanges == null ? 'ALL' : #exchanges", sync = true)
    public Map<String, Map<ExchangeType, String>> perpUniverse(Set<ExchangeType> exchanges) {
        Set<ExchangeType> scope = (exchanges == null || exchanges.isEmpty())
                ? EnumSet.allOf(ExchangeType.class) : EnumSet.copyOf(exchanges);

        Stream<InstrumentData> stream = scope.stream().flatMap(ex -> {
            try {
                ExchangeClient c = client(ex);
                return c.getInstruments().stream();
            } catch (Exception e) {
                return Stream.empty();
            }
        });

        Map<String, Map<ExchangeType, String>> raw = new TreeMap<>();
        stream.filter(i -> i.type() == InstrumentType.PERPETUAL).forEach(i -> {
            String key = SymbolNormalizer.canonicalKey(i);
            raw.computeIfAbsent(key, k -> new EnumMap<>(ExchangeType.class))
                    .put(i.exchangeType(), i.nativeSymbol());
        });

        return UniverseNormalizer.normalize(raw);
    }
}
