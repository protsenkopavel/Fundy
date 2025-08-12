package net.protsenko.fundy.app.exchange.impl.bingx;

import com.fasterxml.jackson.core.type.TypeReference;
import lombok.RequiredArgsConstructor;
import net.protsenko.fundy.app.exchange.ExchangeType;
import net.protsenko.fundy.app.exchange.support.ExchangeMappingSupport;
import net.protsenko.fundy.app.props.BingxConfig;
import net.protsenko.fundy.app.utils.HttpExecutor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class BingxCache implements ExchangeMappingSupport {

    private final HttpExecutor http;
    private final BingxConfig cfg;

    @Override
    public ExchangeType getExchangeType() {
        return ExchangeType.BINGX;
    }

    @Cacheable(cacheNames = "ex-instruments", key = "'BINGX'", sync = true)
    public List<BingxContractItem> contracts() {
        String url = cfg.getBaseUrl() + "/openApi/swap/v2/quote/contracts";
        BingxResponse<List<BingxContractItem>> resp =
                http.get(url, cfg.getTimeout(), new TypeReference<>() {
                });
        require(resp != null && resp.code() == 0 && resp.data() != null,
                () -> "BingX contracts error: " + (resp != null ? resp.msg() : "null"));
        return resp.data();
    }

    @Cacheable(cacheNames = "ex-tickers", key = "'BINGX'", sync = true)
    public Map<String, BingxTickerItem> tickers() {
        String url = cfg.getBaseUrl() + "/openApi/swap/v2/quote/ticker";
        BingxResponse<List<BingxTickerItem>> resp =
                http.get(url, cfg.getTimeout(), new TypeReference<>() {
                });
        require(resp != null && resp.code() == 0 && resp.data() != null,
                () -> "BingX tickers error: " + (resp != null ? resp.msg() : "null"));
        return indexByCanonical(resp.data(), BingxTickerItem::symbol);
    }

    @Cacheable(cacheNames = "ex-funding", key = "'BINGX'", sync = true)
    public Map<String, BingxPremiumIndexItem> funding() {
        String url = cfg.getBaseUrl() + "/openApi/swap/v2/quote/premiumIndex";
        BingxResponse<List<BingxPremiumIndexItem>> resp =
                http.get(url, cfg.getTimeout(), new TypeReference<>() {
                });
        require(resp != null && resp.code() == 0 && resp.data() != null,
                () -> "BingX premiumIndex error: " + (resp != null ? resp.msg() : "null"));
        return indexByCanonical(resp.data(), BingxPremiumIndexItem::symbol);
    }
}
