package net.protsenko.fundy.app.utils;

import net.protsenko.fundy.app.domain.CanonicalInstrument;
import net.protsenko.fundy.app.dto.rs.InstrumentData;
import net.protsenko.fundy.app.exchange.ExchangeType;

import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ExchangeLinkResolver {

    private ExchangeLinkResolver() {
    }

    public static Map<ExchangeType, String> generateTradingLinks(CanonicalInstrument instrument,
                                                                 Map<ExchangeType, String> nativeSymbols) {
        Map<ExchangeType, String> links = new EnumMap<>(ExchangeType.class);

        for (Map.Entry<ExchangeType, String> entry : nativeSymbols.entrySet()) {
            ExchangeType exchange = entry.getKey();
            String nativeSymbol = entry.getValue();
            String link = link(exchange, nativeSymbol, instrument.quote());
            links.put(exchange, link);
        }

        return links;
    }

    public static String link(ExchangeType ex, InstrumentData inst) {
        String base = safeUpper(inst.baseAsset());
        String quote = defaultQuote(safeUpper(inst.quoteAsset()));
        return buildUrl(ex, base, quote);
    }

    public static String link(ExchangeType ex, String nativeSymbol, String quote) {
        String[] pq = splitSymbol(ex, nativeSymbol);
        String base = pq[0];
        String q = pq[1] != null ? pq[1] : defaultQuote(safeUpper(quote));
        return buildUrl(ex, base, q);
    }

    public static String spotLink(ExchangeType ex, String nativeSymbol, String quote) {
        String[] pq = splitSymbol(ex, nativeSymbol);
        String base = pq[0];
        String q = pq[1] != null ? pq[1] : defaultQuote(safeUpper(quote));
        return buildSpotUrl(ex, base, q);
    }

    private static String[] splitSymbol(ExchangeType ex, String nativeSymbol) {
        switch (ex) {
            case BYBIT, BINGX, HTX -> {
                String[] parts = nativeSymbol.split("-");
                if (parts.length > 1) {
                    return new String[]{parts[0], parts[1]};
                } else {
                    return splitByKnownQuote(nativeSymbol);
                }
            }
            case OKX -> {
                boolean isSwap = nativeSymbol.endsWith("-SWAP");
                String core = isSwap ? nativeSymbol.substring(0, nativeSymbol.length() - 5) : nativeSymbol;
                String[] parts = core.split("-");
                if (parts.length > 1) {
                    return new String[]{parts[0], parts[1]};
                } else {
                    return new String[]{nativeSymbol, null};
                }
            }
            case GATEIO, MEXC -> {
                String[] parts = nativeSymbol.split("_");
                if (parts.length > 1) {
                    return new String[]{parts[0], parts[1]};
                } else {
                    return splitByKnownQuote(nativeSymbol);
                }
            }
            case KUCOIN -> {
                String core = nativeSymbol.endsWith("M")
                        ? nativeSymbol.substring(0, nativeSymbol.length() - 1)
                        : nativeSymbol;
                core = core.replaceAll("--+", "-");
                return splitByKnownQuote(core);
            }
            case BITGET -> {
                int i = nativeSymbol.indexOf('_');
                String core = i > 0 ? nativeSymbol.substring(0, i) : nativeSymbol;
                return splitByKnownQuote(core);
            }
            case COINEX -> {
                return splitByKnownQuote(nativeSymbol);
            }
        }
        return new String[]{nativeSymbol, null};
    }

    private static String[] splitByKnownQuote(String s) {
        String S = s.toUpperCase(Locale.ROOT);
        List<String> quotes = List.of("USDT", "USDC", "USD", "USDE", "FDUSD", "TUSD", "DAI");
        for (String q : quotes) {
            if (S.endsWith(q)) {
                String base = s.substring(0, s.length() - q.length());
                return new String[]{base, q};
            }
        }
        return new String[]{s, null};
    }

    private static String buildUrl(ExchangeType ex, String base, String quote) {
        return switch (ex) {
            case BYBIT ->   // https://www.bybit.com/trade/usdt/BTCUSDT
                    "https://www.bybit.com/trade/usdt/" + base + quote;
            case MEXC ->    // https://www.mexc.com/futures/BTC_USDT
                    "https://www.mexc.com/futures/" + base + "_" + quote;
            case KUCOIN ->  // https://www.kucoin.com/futures/trade/XBTUSDTM   (BTC -> XBT)
                    "https://www.kucoin.com/futures/trade/" + kucoinSymbol(base, quote);
            case BITGET ->  // https://www.bitget.com/futures/usdt/BTCUSDT
                    "https://www.bitget.com/futures/usdt/" + base + quote;
            case HTX ->     // https://www.htx.com/futures/linear_swap/exchange/#contract_code=BTC-USDT
                    "https://www.htx.com/futures/linear_swap/exchange/#contract_code=" + base + "-" + quote;
            case OKX ->     // https://www.okx.com/trade-swap/btc-usdt-swap
                    "https://www.okx.com/trade-swap/" + (base + "-" + quote + "-swap").toLowerCase(Locale.ROOT);
            case GATEIO ->  // https://www.gate.com/futures/USDT/BTC_USDT
                    "https://www.gate.com/futures/" + quote + "/" + base + "_" + quote;
            case COINEX ->  // https://www.coinex.com/en/futures/btc-usdt
                    "https://www.coinex.com/en/futures/" + (base + "-" + quote).toLowerCase(Locale.ROOT);
            case BINGX ->   // https://bingx.com/en/perpetual/BTC-USDT
                    "https://bingx.com/en/perpetual/" + base + "-" + quote;
        };
    }

    private static String buildSpotUrl(ExchangeType ex, String base, String quote) {
        return switch (ex) {
            case BYBIT ->   // https://www.bybit.com/en/trade/spot/ZERO/USDT
                    "https://www.bybit.com/en/trade/spot/" + base + "/" + quote;
            case MEXC ->    // https://www.mexc.com/ru-RU/exchange/WXT_USDT
                    "https://www.mexc.com/ru-RU/exchange/" + base + "_" + quote;
            case KUCOIN ->  // https://www.kucoin.com/trade/AAVE3L-USDT
                    "https://www.kucoin.com/trade/" + base;
            case BITGET ->  // https://www.bitget.com/spot/BTCUSDT
                    "https://www.bitget.com/spot/" + base + quote;
            case HTX ->     // https://www.htx.com/trade/uro_usdt?type=spot
                    "https://www.htx.com/trade/" + base.toLowerCase(Locale.ROOT) + "_" + quote.toLowerCase(Locale.ROOT) + "?type=spot";
            case OKX ->     // https://www.okx.com/trade-spot/btc-usdt
                    "https://www.okx.com/trade-spot/" + (base + "-" + quote).toLowerCase(Locale.ROOT);
            case GATEIO ->  // https://www.gate.com/trade/BTC_USDT
                    "https://www.gate.com/trade/" + base + "_" + quote;
            case COINEX ->  // https://www.coinex.com/en/exchange/tomi-usdt
                    "https://www.coinex.com/en/exchange/" + (base + "-" + quote).toLowerCase(Locale.ROOT);
            case BINGX ->   // https://bingx.com/en/spot/XOUSDT
                    "https://bingx.com/en/spot/" + base + quote;
        };
    }

    private static String kucoinSymbol(String base, String quote) {
        String b = "BTC".equals(base) ? "XBT" : base;
        return b + quote + "M";
    }

    private static String defaultQuote(String q) {
        String U = safeUpper(q);
        return U.isBlank() ? "USDT" : U;
    }

    private static String safeUpper(String s) {
        return (s == null) ? "" : s.toUpperCase(Locale.ROOT);
    }
}
