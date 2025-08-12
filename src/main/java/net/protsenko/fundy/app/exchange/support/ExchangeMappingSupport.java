package net.protsenko.fundy.app.exchange.support;

import net.protsenko.fundy.app.dto.InstrumentType;
import net.protsenko.fundy.app.dto.rs.FundingRateData;
import net.protsenko.fundy.app.dto.rs.InstrumentData;
import net.protsenko.fundy.app.dto.rs.TickerData;
import net.protsenko.fundy.app.exception.ExchangeException;
import net.protsenko.fundy.app.exchange.ExchangeType;
import net.protsenko.fundy.app.utils.SymbolNormalizer;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

import static net.protsenko.fundy.app.utils.ExchangeUtils.toBigDecimal;

public interface ExchangeMappingSupport {

    ExchangeType getExchangeType();

    default String ensureSymbol(InstrumentData instrument, String fallback) {
        return ensureSymbol(instrument, () -> fallback);
    }

    default String ensureSymbol(InstrumentData instrument, Supplier<String> fallback) {
        String s = instrument.nativeSymbol();
        return (s != null && !s.isBlank()) ? s : fallback.get();
    }

    default InstrumentData instrument(String base, String quote, InstrumentType type, String nativeSymbol) {
        return new InstrumentData(base, quote, type, nativeSymbol, getExchangeType());
    }

    default TickerData ticker(InstrumentData instrument,
                              BigDecimal last, BigDecimal bid, BigDecimal ask,
                              BigDecimal high24h, BigDecimal low24h, BigDecimal volume24h) {
        return new TickerData(instrument, last, bid, ask, high24h, low24h, volume24h);
    }

    default TickerData ticker(InstrumentData instrument,
                              String last, String bid, String ask,
                              String high24h, String low24h, String volume24h) {
        return ticker(instrument,
                toBigDecimal(last), toBigDecimal(bid), toBigDecimal(ask),
                toBigDecimal(high24h), toBigDecimal(low24h), toBigDecimal(volume24h));
    }

    default FundingRateData funding(InstrumentData instrument, BigDecimal rate, long nextFundingTimeMs) {
        return new FundingRateData(getExchangeType(), instrument, rate, nextFundingTimeMs);
    }

    default FundingRateData funding(InstrumentData instrument, String rate, long nextFundingTimeMs) {
        return funding(instrument, toBigDecimal(rate), nextFundingTimeMs);
    }

    default <S> Map<String, S> indexByCanonical(List<S> items, Function<S, String> sourceNativeSymbol) {
        return indexByCanonical(getExchangeType(), items, sourceNativeSymbol);
    }

    default <S> Map<String, S> indexByCanonical(ExchangeType ex, List<S> items, Function<S, String> sourceNativeSymbol) {
        if (items == null || items.isEmpty()) return Map.of();
        int cap = Math.max(16, (int) (items.size() / 0.75f) + 1);
        Map<String, S> m = new HashMap<>(cap);
        for (S it : items) {
            String nativeSym = sourceNativeSymbol.apply(it);
            String key = SymbolNormalizer.canonicalKey(ex, nativeSym);
            m.putIfAbsent(key, it);
        }
        return m;
    }

    default <S> List<TickerData> mapTickersByCanonical(
            List<InstrumentData> instruments,
            Map<String, S> sourceByCanonical,
            BiFunction<InstrumentData, S, TickerData> mapper
    ) {
        return instruments.stream()
                .map(inst -> {
                    String key = SymbolNormalizer.canonicalKey(inst);
                    S s = sourceByCanonical.get(key);
                    return s != null ? mapper.apply(inst, s) : null;
                })
                .filter(Objects::nonNull)
                .toList();
    }

    default <S> List<FundingRateData> mapFundingByCanonical(
            List<InstrumentData> instruments,
            Map<String, S> sourceByCanonical,
            BiFunction<InstrumentData, S, FundingRateData> mapper
    ) {
        return instruments.stream()
                .map(inst -> {
                    String key = SymbolNormalizer.canonicalKey(inst);
                    S s = sourceByCanonical.get(key);
                    return s != null ? mapper.apply(inst, s) : null;
                })
                .filter(Objects::nonNull)
                .toList();
    }

    default long nextFundingAlignedHours(int hours) {
        require(hours > 0, () -> "hours must be > 0");
        long step = Math.multiplyExact(hours, 3_600_000L);
        long now = System.currentTimeMillis();
        return ((now / step) + 1) * step;
    }

    default void require(boolean ok, Supplier<String> err) {
        if (!ok) throw new ExchangeException("[" + getExchangeType() + "] " + err.get());
    }
}
