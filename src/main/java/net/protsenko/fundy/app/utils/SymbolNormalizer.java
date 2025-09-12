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

    private static final Map<String, String> BASE_ALIASES = new HashMap<>(Map.ofEntries(
            Map.entry("BOBBSC", "BOB"),
            Map.entry("OMNINETWORK", "OMNI"),
            Map.entry("OMNI1", "OMNI"),
            Map.entry("1000000BABYDOGE", "BABYDOGE"),
            Map.entry("10000000AIDOGE", "AIDOGE"),
            Map.entry("1000000CHEEMS", "CHEEMS"),
            Map.entry("1000000MOG", "MOG"),
            Map.entry("100000AIDOGE", "AIDOGE"),
            Map.entry("10000CAT", "CAT"),
            Map.entry("10000ELON", "ELON"),
            Map.entry("10000QUBIC", "QUBIC"),
            Map.entry("10000SATS", "SATS"),
            Map.entry("10000WEN", "WEN"),
            Map.entry("1000BONK", "BONK"),
            Map.entry("1000BTT", "BTT"),
            Map.entry("1000CAT", "CAT"),
            Map.entry("1000CHEEMS", "CHEEMS"),
            Map.entry("1000FLOKI", "FLOKI"),
            Map.entry("1000LUNC", "LUNC"),
            Map.entry("1000NEIROCTO", "NEIROCTO"),
            Map.entry("1000PEPE", "PEPE"),
            Map.entry("1000RATS", "RATS"),
            Map.entry("1000SATS", "SATS"),
            Map.entry("1000TAG", "TAG"),
            Map.entry("1000TOSHI", "TOSHI"),
            Map.entry("1000TURBO", "TURBO"),
            Map.entry("1000X", "X"),
            Map.entry("1000XEC", "XEC"),
            Map.entry("1MBABYDOGE", "MBABYDOGE"),
            Map.entry("MBABYDOGE", "BABYDOGE"),
            Map.entry("LUNA2", "LUNA"),
            Map.entry("LUNANEW", "LUNA"),
            Map.entry("TSTBSC", "TST"),
            Map.entry("ACTSOL", "ACT"),
            Map.entry("ARCSOL", "ARC"),
            Map.entry("TRUMPSOL", "TRUMP"),
            Map.entry("AINBSC", "AIN"),
            Map.entry("10000COQ", "COQ"),
            Map.entry("10000LADYS", "LADYS"),
            Map.entry("1000000PEIPEI", "PEIPEI")
    ));

    public static String canonicalKey(InstrumentData inst) {
        return canonicalKey(inst, true);
    }

    public static String canonicalKey(InstrumentData inst, boolean applyAliases) {
        String base = applyAliases ? BASE_ALIASES.getOrDefault(inst.baseAsset(), inst.baseAsset()) : inst.baseAsset();
        return key(base, inst.quoteAsset());
    }

    public static String canonicalKey(ExchangeType ex, String nativeSymbol) {
        return canonicalKey(ex, nativeSymbol, true);
    }

    public static String canonicalKey(ExchangeType ex, String nativeSymbol, boolean applyAliases) {
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
        String base = applyAliases ? BASE_ALIASES.getOrDefault(pq[0], pq[0]) : pq[0];
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