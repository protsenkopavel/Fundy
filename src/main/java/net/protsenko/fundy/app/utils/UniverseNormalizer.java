package net.protsenko.fundy.app.utils;

import net.protsenko.fundy.app.exchange.ExchangeType;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class UniverseNormalizer {

    private static final Pattern DELIVERY_SUFFIX = Pattern.compile(".*-\\d{2}[A-Z]{3}\\d{2}$");
    private static final Pattern METRIC_PREFIX = Pattern.compile("^(\\d+)([KMB])(.*)$");
    private static final Set<String> ALLOWED_QUOTES = Set.of("USDT", "USDC", "USD");

    private static final Map<String, String> BASE_ALIASES = Map.ofEntries(
        Map.entry("AINBSC", "AIN"),
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
        Map.entry("BOBBSC", "BOB"),
        Map.entry("10000COQ", "COQ"),
        Map.entry("10000LADYS", "LADYS"),
        Map.entry("1000000PEIPEI", "PEIPEI")
    );

    private UniverseNormalizer() {
    }

    public static Map<String, Map<ExchangeType, String>> normalize(Map<String, Map<ExchangeType, String>> raw) {
        Map<String, Map<ExchangeType, String>> out = new TreeMap<>();

        raw.forEach((key, exMap) -> {
            String normKey = normalizeKey(key);
            if (normKey == null) return;

            Map<ExchangeType, String> filtered = new EnumMap<>(ExchangeType.class);
            exMap.forEach((ex, sym) -> {
                String clean = sanitizeNative(sym);
                if (clean != null && isPerpSymbol(ex, clean)) {
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

    public static Map<String, Map<ExchangeType, String>> normalizeSpot(Map<String, Map<ExchangeType, String>> raw) {
        Map<String, Map<ExchangeType, String>> out = new TreeMap<>();

        raw.forEach((key, exMap) -> {
            String normKey = normalizeKey(key);
            if (normKey == null) return;

            Map<ExchangeType, String> filtered = new EnumMap<>(ExchangeType.class);
            exMap.forEach((ex, sym) -> {
                String clean = sanitizeNative(sym);
                if (clean != null && isSpotSymbol(ex, clean)) {
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

    public static String normalizeKey(String rawKey) {
        if (rawKey == null) return null;
        String s = rawKey.trim().toUpperCase(Locale.ROOT);
        int slash = s.indexOf('/');
        if (slash <= 0 || slash >= s.length() - 1) return null;

        String base = s.substring(0, slash).trim();
        String quote = s.substring(slash + 1).trim();

        if (!ALLOWED_QUOTES.contains(quote)) return null;

        base = base.replaceFirst("^\\$+", "");
        base = expandMetricPrefix(base);
        base = BASE_ALIASES.getOrDefault(base, base);

        return base + "/" + quote;
    }

    private static String expandMetricPrefix(String base) {
        Matcher m = METRIC_PREFIX.matcher(base);
        if (!m.find()) return base;

        long n = Long.parseLong(m.group(1));
        long mult = switch (m.group(2)) {
            case "K" -> 1_000L;
            case "M" -> 1_000_000L;
            case "B" -> 1_000_000_000L;
            default -> 1L;
        };
        String rest = m.group(3);
        return (n * mult) + rest;
    }

    private static boolean isPerpSymbol(ExchangeType ex, String symbol) {
        String s = symbol.trim().toUpperCase(Locale.ROOT);
        return switch (ex) {
            case BYBIT -> !DELIVERY_SUFFIX.matcher(s).matches() &&
                    (s.endsWith("USDT") || s.endsWith("USDC") || s.endsWith("PERP"));
            case OKX -> s.contains("-SWAP");
            case KUCOIN -> s.endsWith("USDTM") || s.endsWith("USDM") || s.endsWith("USDCM");
            case BITGET -> s.endsWith("_UMCBL") || s.endsWith("_CMCBL");
            case BINGX, HTX -> s.contains("-USDT") || s.contains("-USDC");
            case GATEIO, MEXC -> s.contains("_USDT") || s.contains("_USDC") || s.contains("_USD");
            case COINEX -> s.endsWith("USDT") || s.endsWith("USDC");
        };
    }

    private static boolean isSpotSymbol(ExchangeType ex, String symbol) {
        String s = symbol.trim().toUpperCase(Locale.ROOT);
        return switch (ex) {
            case BYBIT -> s.endsWith("USDT") || s.endsWith("USDC") || s.endsWith("USD");
            case OKX -> s.contains("-") && !s.contains("-SWAP") && !s.contains("-FUTURES");
            case KUCOIN -> s.contains("-") && !s.endsWith("USDTM") && !s.endsWith("USDM") && !s.endsWith("USDCM");
            case BITGET -> s.contains("USDT_SPBL") || s.contains("USDC_SPBL") || s.contains("USD_SPBL");
            case BINGX -> s.contains("-USDT") || s.contains("-USDC") || s.contains("-USD");
            case HTX -> s.endsWith("USDT") || s.endsWith("USDC") || s.endsWith("USD");
            case GATEIO -> s.contains("_USDT") || s.contains("_USDC") || s.contains("_USD");
            case MEXC -> s.endsWith("USDT") || s.endsWith("USDC") || s.endsWith("USD");
            case COINEX -> s.endsWith("USDT") || s.endsWith("USDC") || s.endsWith("USD");
        };
    }

    private static String sanitizeNative(String symbol) {
        if (symbol == null) return null;
        return symbol.trim().replaceFirst("^\\$+", "");
    }
}
