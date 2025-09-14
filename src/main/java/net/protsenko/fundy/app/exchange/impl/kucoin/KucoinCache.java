package net.protsenko.fundy.app.exchange.impl.kucoin;

import com.fasterxml.jackson.core.type.TypeReference;
import lombok.RequiredArgsConstructor;
import net.protsenko.fundy.app.exchange.ExchangeType;
import net.protsenko.fundy.app.exchange.support.ExchangeMappingSupport;
import net.protsenko.fundy.app.props.KucoinConfig;
import net.protsenko.fundy.app.utils.HttpExecutor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.util.List;
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
        String tickersUrl = cfg.getBaseUrl() + "/api/v1/allTickers";
        KucoinAllTickersResponse tickersResp = http.get(tickersUrl, cfg.getTimeout(), KucoinAllTickersResponse.class);
        require(tickersResp != null && "200000".equals(tickersResp.code()) && tickersResp.data() != null,
                () -> "KuCoin allTickers error");

        String contractsUrl = cfg.getBaseUrl() + "/api/v1/contracts/active";
        KucoinContractsResponse contractsResp = http.get(contractsUrl, cfg.getTimeout(), KucoinContractsResponse.class);
        require(contractsResp != null && contractsResp.data() != null, () -> "KuCoin contracts fetch error");

        Map<String, KucoinContractItem> contractsBySymbol = contractsResp.data().stream()
                .collect(Collectors.toMap(KucoinContractItem::symbol, contract -> contract));

        List<KucoinTickerData> mergedTickers = tickersResp.data().stream()
                .map(ticker -> {
                    KucoinContractItem contract = contractsBySymbol.get(ticker.symbol());
                    if (contract != null) {
                        String price = ticker.price() != null ? ticker.price() : contract.lastTradePrice();
                        String high = ticker.high() != null ? ticker.high() : contract.highPrice();
                        String low = ticker.low() != null ? ticker.low() : contract.lowPrice();
                        String vol = ticker.vol() != null ? ticker.vol() : contract.volumeOf24h();

                        return new KucoinTickerData(
                                ticker.sequence(),
                                ticker.symbol(),
                                ticker.side(),
                                ticker.size(),
                                ticker.tradeId(),
                                price,
                                ticker.bestBidPrice(),
                                ticker.bestAskPrice(),
                                ticker.bestBidSize(),
                                ticker.bestAskSize(),
                                ticker.ts(),
                                high,
                                low,
                                vol
                        );
                    }
                    return ticker;
                })
                .toList();

        return indexByCanonical(mergedTickers, KucoinTickerData::symbol);
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
        String url = cfg.getSpotBaseUrl() + "/api/v2/symbols";
        var resp = http.get(url, cfg.getTimeout(), new TypeReference<Map<String, Object>>() {});
        require(resp != null && resp.containsKey("data"),
                () -> "KuCoin spot instruments error: invalid response");

        @SuppressWarnings("unchecked")
        var symbols = (List<Map<String, Object>>) resp.get("data");

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
        String url = cfg.getSpotBaseUrl() + "/api/v1/market/allTickers";
        KucoinSpotAllTickersResponse resp = http.get(url, cfg.getTimeout(), KucoinSpotAllTickersResponse.class);
        require(resp != null && "200000".equals(resp.code()) && resp.data() != null && resp.data().ticker() != null,
                () -> "KuCoin spot allTickers error");
        return indexByCanonical(resp.data().ticker(), KucoinTickerData::symbol);
    }
}