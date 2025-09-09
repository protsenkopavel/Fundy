package net.protsenko.fundy.app.exchange.impl.kucoin;

import com.fasterxml.jackson.core.type.TypeReference;
import lombok.RequiredArgsConstructor;
import net.protsenko.fundy.app.exchange.ExchangeType;
import net.protsenko.fundy.app.exchange.support.ExchangeMappingSupport;
import net.protsenko.fundy.app.props.KucoinConfig;
import net.protsenko.fundy.app.utils.HttpExecutor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class KucoinCache implements ExchangeMappingSupport {

    private final HttpExecutor http;
    private final KucoinConfig cfg;

    @Override
    public ExchangeType getExchangeType() {
        return ExchangeType.KUCOIN;
    }

    @Cacheable(cacheNames = "ex-tickers", key = "'KUCOIN'", sync = true)
    public Map<String, KucoinTickerData> tickers() {
        String url = cfg.getBaseUrl() + "/api/v1/allTickers";
        KucoinAllTickersResponse resp = http.get(url, cfg.getTimeout(), KucoinAllTickersResponse.class);
        require(resp != null && "200000".equals(resp.code()) && resp.data() != null,
                () -> "KuCoin allTickers error");
        return indexByCanonical(resp.data(), KucoinTickerData::symbol);
    }

    @Cacheable(cacheNames = "ex-instruments", key = "'KUCOIN'", sync = true)
    public Map<String, KucoinContractItem> contracts() {
        String url = cfg.getBaseUrl() + "/api/v1/contracts/active";
        KucoinContractsResponse resp = http.get(url, cfg.getTimeout(), KucoinContractsResponse.class);
        require(resp != null && resp.data() != null, () -> "KuCoin contracts fetch error");
        return indexByCanonical(resp.data(), KucoinContractItem::symbol);
    }

    @Cacheable(cacheNames = "ex-spot-instruments", key = "'KUCOIN'", sync = true)
    public Map<String, KucoinSpotInstrumentItem> spotInstruments() {
        String url = "https://api.kucoin.com/api/v2/symbols";
        var resp = http.get(url, cfg.getTimeout(), new TypeReference<Map<String, Object>>() {});
        require(resp != null && resp.containsKey("data"),
                () -> "KuCoin spot instruments error: invalid response");

        @SuppressWarnings("unchecked")
        var symbols = (java.util.List<java.util.Map<String, Object>>) resp.get("data");

        return symbols.stream()
                .filter(s -> "true".equals(s.get("enableTrading").toString()))
                .map(s -> {
                    String symbol = (String) s.get("symbol");
                    String baseCurrency = (String) s.get("baseCurrency");
                    String quoteCurrency = (String) s.get("quoteCurrency");
                    return new KucoinSpotInstrumentItem(symbol, baseCurrency, quoteCurrency);
                })
                .collect(Collectors.toMap(
                        KucoinSpotInstrumentItem::symbol,
                        item -> item,
                        (existing, replacement) -> existing
                ));
    }

    @Cacheable(cacheNames = "ex-spot-tickers", key = "'KUCOIN'", sync = true)
    public Map<String, KucoinTickerData> spotTickers() {
        Map<String, KucoinTickerData> allTickers = tickers();
        Map<String, KucoinSpotInstrumentItem> spotInstruments = spotInstruments();

        return allTickers.entrySet().stream()
                .filter(entry -> spotInstruments.containsKey(entry.getKey()))
                .collect(Collectors.toMap(
                        java.util.Map.Entry::getKey,
                        java.util.Map.Entry::getValue
                ));
    }
}