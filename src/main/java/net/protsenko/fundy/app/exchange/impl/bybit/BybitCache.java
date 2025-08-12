package net.protsenko.fundy.app.exchange.impl.bybit;

import lombok.RequiredArgsConstructor;
import net.protsenko.fundy.app.exchange.ExchangeType;
import net.protsenko.fundy.app.exchange.support.ExchangeMappingSupport;
import net.protsenko.fundy.app.props.BybitConfig;
import net.protsenko.fundy.app.utils.HttpExecutor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class BybitCache implements ExchangeMappingSupport {

    private final HttpExecutor http;
    private final BybitConfig cfg;

    @Override
    public ExchangeType getExchangeType() {
        return ExchangeType.BYBIT;
    }

    @Cacheable(cacheNames = "ex-instruments", key = "'BYBIT'", sync = true)
    public List<BybitInstrumentItem> instruments() {
        String url = cfg.getBaseUrl() + "/v5/market/instruments-info?category=linear";
        BybitInstrumentsResponse resp = http.get(url, cfg.getTimeout(), BybitInstrumentsResponse.class);
        require(resp != null && resp.retCode() == 0 && resp.result() != null,
                () -> "Bybit instruments error: " + (resp != null ? resp.retMsg() : "null response"));
        return resp.result().list();
    }

    @Cacheable(cacheNames = "ex-tickers", key = "'BYBIT'", sync = true)
    public Map<String, BybitTickerItem> tickers() {
        String url = cfg.getBaseUrl() + "/v5/market/tickers?category=linear";
        BybitTickerResponse resp = http.get(url, cfg.getTimeout(), BybitTickerResponse.class);
        require(resp != null && resp.retCode() == 0 && resp.result() != null,
                () -> "Bybit tickers error: " + (resp != null ? resp.retMsg() : "null response"));
        return indexByCanonical(resp.result().list(), BybitTickerItem::symbol);
    }
}
