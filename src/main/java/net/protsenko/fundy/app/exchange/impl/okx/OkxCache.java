package net.protsenko.fundy.app.exchange.impl.okx;

import com.fasterxml.jackson.core.type.TypeReference;
import lombok.RequiredArgsConstructor;
import net.protsenko.fundy.app.exchange.ExchangeType;
import net.protsenko.fundy.app.exchange.support.ExchangeMappingSupport;
import net.protsenko.fundy.app.props.OkxConfig;
import net.protsenko.fundy.app.utils.HttpExecutor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class OkxCache implements ExchangeMappingSupport {

    private final HttpExecutor http;
    private final OkxConfig cfg;

    @Override
    public ExchangeType getExchangeType() {
        return ExchangeType.OKX;
    }

    @Cacheable(cacheNames = "ex-instruments", key = "'OKX'", sync = true)
    public List<OkxInstrumentItem> instruments() {
        String url = cfg.getBaseUrl() + "/api/v5/public/instruments?instType=SWAP";
        OkxResponse<OkxInstrumentItem> resp =
                http.get(url, cfg.getTimeout(), new TypeReference<>() {
                });
        require(resp != null && "0".equals(resp.code()) && resp.data() != null,
                () -> "OKX instruments error: " + (resp != null ? resp.msg() : "null"));
        return resp.data();
    }

    @Cacheable(cacheNames = "ex-tickers", key = "'OKX'", sync = true)
    public Map<String, OkxTickerItem> tickers() {
        String url = cfg.getBaseUrl() + "/api/v5/market/tickers?instType=SWAP";
        OkxResponse<OkxTickerItem> resp =
                http.get(url, cfg.getTimeout(), new TypeReference<>() {
                });
        require(resp != null && "0".equals(resp.code()) && resp.data() != null,
                () -> "OKX all-tickers error: " + (resp != null ? resp.msg() : "null"));
        return indexByCanonical(resp.data(), OkxTickerItem::instId);
    }

    @Cacheable(cacheNames = "ex-funding", key = "'OKX:' + #instId", sync = true)
    public OkxFundingItem fundingSingle(String instId) {
        String url = cfg.getBaseUrl() + "/api/v5/public/funding-rate?instId=" + instId;
        OkxResponse<OkxFundingItem> resp =
                http.get(url, cfg.getTimeout(), new TypeReference<>() {
                });
        require(resp != null && "0".equals(resp.code()) && resp.data() != null && !resp.data().isEmpty(),
                () -> "OKX funding error for " + instId + ": " + (resp != null ? resp.msg() : "null"));
        return resp.data().getFirst();
    }
}
