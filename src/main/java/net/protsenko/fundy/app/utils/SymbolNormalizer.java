package net.protsenko.fundy.app.utils;

import lombok.experimental.UtilityClass;
import net.protsenko.fundy.app.dto.rs.InstrumentData;
import net.protsenko.fundy.app.exchange.ExchangeType;

import java.util.*;

@UtilityClass
public class SymbolNormalizer {

    private static final List<String> QUOTES = List.of(
            "USDT", "USDC", "USD", "USDE", "FDUSD", "TUSD", "DAI"
    );

    private static final Map<String, String> BASE_ALIASES = new HashMap<>(Map.of(
            "BOBBSC", "BOB"
    ));

    public static void putBaseAlias(String dirty, String clean) {
        BASE_ALIASES.put(dirty.toUpperCase(Locale.ROOT), clean.toUpperCase(Locale.ROOT));
    }

    public static String canonicalKey(InstrumentData inst) {
        return key(inst.baseAsset(), inst.quoteAsset());
    }

    public static String canonicalKey(ExchangeType ex, String nativeSymbol) {
        String[] pq = switch (ex) {
            case BYBIT -> splitBybit(nativeSymbol);
            case BINGX, HTX -> splitByDash(nativeSymbol);
            case OKX -> splitOkx(nativeSymbol);
            case GATEIO -> splitGate(nativeSymbol);
            case KUCOIN -> splitKucoin(nativeSymbol);
            case BITGET -> splitBitget(nativeSymbol);
            case COINEX -> splitCoinex(nativeSymbol);
            case MEXC -> splitMexc(nativeSymbol);
        };
        String base = BASE_ALIASES.getOrDefault(pq[0], pq[0]);
        return key(base, pq[1]);
    }

    private static String key(String base, String quote) {
        return base.toUpperCase(Locale.ROOT) + "/" + quote.toUpperCase(Locale.ROOT);
    }

    private static String[] splitByDash(String s) {
        String[] p = s.split("-");
        String base = p.length > 0 ? p[0] : s;
        String quote = p.length > 1 ? p[1] : guessQuote();
        return new String[]{base, quote};
    }

    private static String[] splitGate(String s) {
        String[] p = s.split("_");
        String base = p.length > 0 ? p[0] : s;
        String quote = p.length > 1 ? p[1] : guessQuote();
        return new String[]{base, quote};
    }

    private static String[] splitOkx(String s) {
        String core = s.endsWith("-SWAP") ? s.substring(0, s.length() - 5) : s;
        return splitByDash(core);
    }

    private static String[] splitKucoin(String s) {
        String core = s.endsWith("M") ? s.substring(0, s.length() - 1) : s;
        return splitByKnownQuote(core);
    }

    private static String[] splitBitget(String s) {
        int i = s.indexOf('_');
        String core = i > 0 ? s.substring(0, i) : s;
        return splitByKnownQuote(core);
    }

    private static String[] splitCoinex(String s) {
        return splitByKnownQuote(s);
    }

    private static String[] splitBybit(String s) {
        return splitByKnownQuote(s);
    }

    private static String[] splitMexc(String s) {
        return splitGate(s);
    }

    private static String[] splitByKnownQuote(String s) {
        String S = s.toUpperCase(Locale.ROOT);
        List<String> sorted = QUOTES.stream()
                .sorted(Comparator.comparingInt(String::length).reversed())
                .toList();
        for (String q : sorted) {
            if (S.endsWith(q)) {
                String base = s.substring(0, s.length() - q.length());
                return new String[]{base, q};
            }
        }
        return new String[]{s, guessQuote()};
    }

    private static String guessQuote() {
        return "USDT";
    }
}