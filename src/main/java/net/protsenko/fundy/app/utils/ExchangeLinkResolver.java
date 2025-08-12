package net.protsenko.fundy.app.utils;

import net.protsenko.fundy.app.dto.rs.InstrumentData;
import net.protsenko.fundy.app.exchange.ExchangeType;

import java.util.*;
import java.util.regex.Pattern;

public final class ExchangeLinkResolver {

    private static final Set<String> KNOWN_QUOTES = new LinkedHashSet<>(Arrays.asList(
            "USDT", "USDC", "USD", "USDE", "FDUSD", "TUSD", "DAI", "USDD", "FDUSDT", "EURT"
    ));

    private static final List<String> DERIV_SUFFIXES = Arrays.asList(
            "SWAP", "PERP", "USDTM", "USDM", "UMCBL", "CMCBL", "DMCBL"
    );
    private static final Pattern SPLIT_STD = Pattern.compile("^([A-Za-z0-9]+)[-_/]([A-Za-z0-9]+)$");

    private ExchangeLinkResolver() {
    }

    public static String link(ExchangeType ex, InstrumentData inst) {
        String base = safeUpper(inst.baseAsset());
        String quote = defaultQuote(safeUpper(inst.quoteAsset()));
        return buildUrl(ex, base, quote);
    }

    private static String buildUrl(ExchangeType ex, String base, String quote) {
        switch (ex) {
            case BYBIT:   // https://www.bybit.com/trade/usdt/BTCUSDT
                return "https://www.bybit.com/trade/usdt/" + base + quote;

            case MEXC:    // https://www.mexc.com/futures/BTC_USDT
                return "https://www.mexc.com/futures/" + base + "_" + quote;

            case KUCOIN:  // https://www.kucoin.com/futures/trade/XBTUSDTM   (BTC -> XBT)
                return "https://www.kucoin.com/futures/trade/" + kucoinSymbol(base, quote);

            case BITGET:  // https://www.bitget.com/futures/usdt/BTCUSDT
                return "https://www.bitget.com/futures/usdt/" + base + quote;

            case HTX:     // https://www.htx.com/futures/linear_swap/exchange/#contract_code=BTC-USDT
                return "https://www.htx.com/futures/linear_swap/exchange/#contract_code=" + base + "-" + quote;

            case OKX:     // https://www.okx.com/trade-swap/btc-usdt-swap
                return "https://www.okx.com/trade-swap/" + (base + "-" + quote + "-swap").toLowerCase(Locale.ROOT);

            case GATEIO:  // https://www.gate.com/futures/USDT/BTC_USDT
                return "https://www.gate.com/futures/" + quote + "/" + base + "_" + quote;

            case COINEX:  // https://www.coinex.com/en/futures/btc-usdt
                return "https://www.coinex.com/en/futures/" + (base + "-" + quote).toLowerCase(Locale.ROOT);

            case BINGX:   // https://bingx.com/en/perpetual/BTC-USDT
                return "https://bingx.com/en/perpetual/" + base + "-" + quote;
        }
        return "";
    }

    private static String kucoinSymbol(String base, String quote) {
        String b = "BTC".equals(base) ? "XBT" : base;
        return b + quote + "M";
    }

    private static String defaultQuote(String q) {
        String U = safeUpper(q);
        return (U == null || U.isBlank()) ? "USDT" : U;
    }

    private static String safeUpper(String s) {
        return (s == null) ? "" : s.toUpperCase(Locale.ROOT);
    }
}
