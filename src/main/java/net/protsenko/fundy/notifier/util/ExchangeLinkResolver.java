package net.protsenko.fundy.notifier.util;


import net.protsenko.fundy.app.dto.TradingInstrument;
import net.protsenko.fundy.app.exchange.ExchangeType;

public final class ExchangeLinkResolver {

    private ExchangeLinkResolver() {
    }

    public static String link(ExchangeType ex, TradingInstrument inst) {
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
}
