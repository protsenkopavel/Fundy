package net.protsenko.fundy.app.exchange.impl.mexc;

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
}
