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
import java.util.Set;

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
                return c.getFuturesInstruments().stream();
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

    @Cacheable(cacheNames = "universe-spot-24h", key = "#exchanges == null ? 'ALL' : #exchanges", sync = true)
    public Map<String, Map<ExchangeType, String>> spotUniverse(Set<ExchangeType> exchanges) {
        Set<ExchangeType> scope = (exchanges == null || exchanges.isEmpty())
                ? EnumSet.allOf(ExchangeType.class) : EnumSet.copyOf(exchanges);

        Stream<InstrumentData> stream = scope.stream().flatMap(ex -> {
            try {
                ExchangeClient c = client(ex);
                return c.getSpotInstruments().stream();
            } catch (Exception e) {
                return Stream.empty();
            }
        });

        Map<String, Map<ExchangeType, String>> raw = new TreeMap<>();
        stream.filter(i -> i.type() == InstrumentType.SPOT).forEach(i -> {
            String key = SymbolNormalizer.canonicalKey(i);
            raw.computeIfAbsent(key, k -> new EnumMap<>(ExchangeType.class))
                    .put(i.exchangeType(), i.nativeSymbol());
        });

        return normalizeSpotUniverse(raw);
    }

    private Map<String, Map<ExchangeType, String>> normalizeSpotUniverse(Map<String, Map<ExchangeType, String>> raw) {
        Map<String, Map<ExchangeType, String>> out = new TreeMap<>();

        raw.forEach((key, exMap) -> {
            String normKey = normalizeSpotKey(key);
            if (normKey == null) return;

            Map<ExchangeType, String> filtered = new EnumMap<>(ExchangeType.class);
            exMap.forEach((ex, sym) -> {
                String clean = sanitizeNative(sym);
                if (clean != null) {
                    filtered.put(ex, clean);
                }
            });

            if (!filtered.isEmpty()) {
                out.computeIfAbsent(normKey, k -> new EnumMap<>(ExchangeType.class))
                        .putAll(filtered);
            }
        });

        return out;
    }

    private String normalizeSpotKey(String rawKey) {
        if (rawKey == null) return null;
        String s = rawKey.trim().toUpperCase(Locale.ROOT);
        int slash = s.indexOf('/');
        if (slash <= 0 || slash >= s.length() - 1) return null;

        String base = s.substring(0, slash).trim();
        String quote = s.substring(slash + 1).trim();

        Set<String> allowedSpotQuotes = Set.of("USDT", "USDC", "USD", "BTC", "ETH", "EUR", "BRL", "TRY", "AUD", "AED", "SGD");
        if (!allowedSpotQuotes.contains(quote)) return null;

        base = base.replaceFirst("^\\$+", "");
        return base + "/" + quote;
    }

    private String sanitizeNative(String symbol) {
        if (symbol == null) return null;
        return symbol.trim().replaceFirst("^\\$+", "");
    }
}
