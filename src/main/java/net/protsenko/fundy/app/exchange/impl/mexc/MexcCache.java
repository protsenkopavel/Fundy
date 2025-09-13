package net.protsenko.fundy.app.exchange.impl.mexc;

import com.fasterxml.jackson.core.type.TypeReference;
import lombok.RequiredArgsConstructor;
import net.protsenko.fundy.app.exchange.ExchangeType;
import net.protsenko.fundy.app.exchange.support.ExchangeMappingSupport;
import net.protsenko.fundy.app.props.MexcConfig;
import net.protsenko.fundy.app.utils.HttpExecutor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class MexcCache implements ExchangeMappingSupport {

    private final HttpExecutor http;
    private final MexcConfig cfg;

    @Override
    public ExchangeType getExchangeType() {
        return ExchangeType.MEXC;
    }

    @Cacheable(cacheNames = "ex-instruments", key = "'MEXC'", sync = true)
    public List<MexcInstrumentItem> instruments() {
        String url = cfg.getBaseUrl() + "/api/v1/contract/detail";
        MexcInstrumentsResponse resp = http.get(url, cfg.getTimeout(), MexcInstrumentsResponse.class);
        require(resp != null && resp.code() == 0 && resp.data() != null,
                () -> "MEXC instruments error: " + (resp != null ? resp.msg() : "null response"));
        return resp.data();
    }

    @Cacheable(cacheNames = "ex-tickers", key = "'MEXC'", sync = true)
    public Map<String, MexcTickerItem> tickers() {
        String url = cfg.getBaseUrl() + "/api/v1/contract/ticker";
        MexcTickerListWrapper resp = http.get(url, cfg.getTimeout(), MexcTickerListWrapper.class);
        require(resp != null && resp.code() == 0 && resp.data() != null,
                () -> "MEXC tickers error: " + (resp != null ? resp.msg() : "null response"));
        return indexByCanonical(resp.data(), MexcTickerItem::symbol);
    }

    @Cacheable(cacheNames = "ex-funding", key = "'MEXC'", sync = true)
    public Map<String, MexcFundingItem> funding() {
        String url = cfg.getBaseUrl() + "/api/v1/contract/funding_rate";
        MexcFundingListResponse resp = http.get(url, cfg.getTimeout(), MexcFundingListResponse.class);
        require(resp != null && resp.code() == 0 && resp.data() != null && !resp.data().isEmpty(),
                () -> "MEXC funding error: " + (resp != null ? resp.msg() : "null response"));
        return indexByCanonical(resp.data(), MexcFundingItem::symbol);
    }

    @Cacheable(cacheNames = "ex-spot-instruments", key = "'MEXC'", sync = true)
    public List<MexcInstrumentItem> spotInstruments() {
        String url = cfg.getSpotBaseUrl() + "/api/v3/exchangeInfo";
        var resp = http.get(url, cfg.getTimeout(), new TypeReference<Map<String, Object>>() {});
        require(resp != null && resp.containsKey("symbols"),
                () -> "MEXC spot instruments error: invalid response");

        @SuppressWarnings("unchecked")
        var symbols = (List<Map<String, Object>>) resp.get("symbols");

        return symbols.stream()
                .filter(s -> {
                    Object status = s.get("status");
                    Object isSpotTradingAllowed = s.get("isSpotTradingAllowed");
                    return (status != null && "1".equals(status.toString()))
                            && (Boolean.TRUE.equals(isSpotTradingAllowed));
                })
                .map(s -> {
                    String symbol = (String) s.get("symbol");
                    String baseAsset = (String) s.get("baseAsset");
                    String quoteAsset = (String) s.get("quoteAsset");
                    return new MexcInstrumentItem(symbol, baseAsset, quoteAsset, 0);
                })
                .toList();
    }

    @Cacheable(cacheNames = "ex-spot-tickers", key = "'MEXC'", sync = true)
    public Map<String, MexcTickerItem> spotTickers() {
        String url = cfg.getSpotBaseUrl() + "/api/v3/ticker/24hr";
        var resp = http.get(url, cfg.getTimeout(), new TypeReference<List<Map<String, Object>>>() {});
        require(resp != null && !resp.isEmpty(),
                () -> "MEXC spot tickers error: null or empty response");

        return indexByCanonical(resp.stream()
                .map(t -> {
                    String symbol = (String) t.get("symbol");
                    String lastPrice = (String) t.get("lastPrice");
                    String bidPrice = (String) t.get("bidPrice");
                    String askPrice = (String) t.get("askPrice");
                    String highPrice = (String) t.get("highPrice");
                    String lowPrice = (String) t.get("lowPrice");
                    String volume = (String) t.get("volume");
                    return new MexcTickerItem(symbol, lastPrice, bidPrice, askPrice, highPrice, lowPrice, volume);
                })
                .toList(), MexcTickerItem::symbol);
    }
}
