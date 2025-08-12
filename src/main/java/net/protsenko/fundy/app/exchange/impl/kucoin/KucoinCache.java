package net.protsenko.fundy.app.exchange.impl.kucoin;

import lombok.RequiredArgsConstructor;
import net.protsenko.fundy.app.exchange.ExchangeType;
import net.protsenko.fundy.app.exchange.support.ExchangeMappingSupport;
import net.protsenko.fundy.app.props.KucoinConfig;
import net.protsenko.fundy.app.utils.HttpExecutor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.util.Map;

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
}