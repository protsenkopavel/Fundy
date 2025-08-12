package net.protsenko.fundy.app.exchange.impl.coinex;

import com.fasterxml.jackson.core.type.TypeReference;
import lombok.RequiredArgsConstructor;
import net.protsenko.fundy.app.exchange.ExchangeType;
import net.protsenko.fundy.app.exchange.support.ExchangeMappingSupport;
import net.protsenko.fundy.app.props.CoinexConfig;
import net.protsenko.fundy.app.utils.HttpExecutor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class CoinexCache implements ExchangeMappingSupport {

    private final HttpExecutor http;
    private final CoinexConfig cfg;

    @Override
    public ExchangeType getExchangeType() {
        return ExchangeType.COINEX;
    }

    @Cacheable(cacheNames = "ex-instruments", key = "'COINEX'", sync = true)
    public List<CoinexContractItem> contracts() {
        String url = cfg.getBaseUrl() + "/perpetual/v1/market/list";
        CoinexResponse<List<CoinexContractItem>> resp =
                http.get(url, cfg.getTimeout(), new TypeReference<>() {
                });
        require(resp != null && resp.code() == 0 && resp.data() != null,
                () -> "CoinEx instruments error: " + (resp != null ? resp.message() : "null"));
        return resp.data();
    }

    @Cacheable(cacheNames = "ex-tickers", key = "'COINEX'", sync = true)
    public Map<String, Map.Entry<String, CoinexTickerItem>> allTickers() {
        String url = cfg.getBaseUrl() + "/perpetual/v1/market/ticker/all";
        CoinexResponse<CoinexTickerAllData> resp =
                http.get(url, cfg.getTimeout(), new TypeReference<>() {
                });
        require(resp != null && resp.code() == 0 && resp.data() != null && resp.data().ticker() != null,
                () -> "CoinEx ticker/all error: " + (resp != null ? resp.message() : "null"));
        return indexByCanonical(resp.data().ticker().entrySet().stream().toList(), Map.Entry::getKey);
    }
}