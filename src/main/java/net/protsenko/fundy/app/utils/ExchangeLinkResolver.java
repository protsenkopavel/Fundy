package net.protsenko.fundy.app.utils;

import net.protsenko.fundy.app.dto.InstrumentType;
import net.protsenko.fundy.app.dto.rs.InstrumentData;
import net.protsenko.fundy.app.exchange.ExchangeType;

public final class ExchangeLinkResolver {

    private ExchangeLinkResolver() {
    }

    public static String link(ExchangeType ex, InstrumentData inst) {
        String base = inst.baseAsset().toUpperCase();
        String quote = inst.quoteAsset().toUpperCase();

        return switch (ex) {
            case BYBIT -> "https://www.bybit.com/trade/usdt/" + base + quote;
            case MEXC -> "https://futures.mexc.com/exchange/" + base + "_" + quote;
            case KUCOIN -> "https://futures.kucoin.com/trade/" + base + quote + "M";
            case BITGET -> "https://www.bitget.com/futures/usdt/" + base + quote;
            case HTX -> "https://www.htx.com/futures/linear_swap/exchange/#contract_code=" + base + "-" + quote;
            case OKX -> "https://www.okx.com/trade-swap/" + base.toLowerCase() + "-" + quote.toLowerCase() + "-swap";
            case GATEIO -> "https://www.gate.io/futures/usdt/" + base + "_" + quote;
            case COINEX -> "https://www.coinex.com/futures/" + base + "-" + quote;
            case BINGX -> "https://bingx.com/perpetual/" + base + "-" + quote;
        };
    }

    public static String link(ExchangeType ex, String symbol) {
        String base;
        String quote;
        if (symbol == null || symbol.isBlank()) {
            base = "";
            quote = "";
        } else if (symbol.contains("-")) {
            String[] parts = symbol.split("-", 2);
            base = parts[0];
            quote = parts[1];
        } else if (symbol.matches("^[A-Za-z]+[A-Za-z]+$")) {
            int len = symbol.length();
            int half = len / 2;
            base = symbol.substring(0, half);
            quote = symbol.substring(half);
        } else {
            base = symbol;
            quote = "";
        }
        InstrumentData inst = new InstrumentData(base, quote, InstrumentType.PERPETUAL, symbol, ex);
        return link(ex, inst);
    }
}
