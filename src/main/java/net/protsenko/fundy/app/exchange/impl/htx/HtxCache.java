package net.protsenko.fundy.app.exchange.impl.htx;

import com.fasterxml.jackson.core.type.TypeReference;
import lombok.RequiredArgsConstructor;
import net.protsenko.fundy.app.exchange.ExchangeType;
import net.protsenko.fundy.app.exchange.support.ExchangeMappingSupport;
import net.protsenko.fundy.app.props.HtxConfig;
import net.protsenko.fundy.app.utils.HttpExecutor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class HtxCache implements ExchangeMappingSupport {

    private final HttpExecutor http;
    private final HtxConfig cfg;

    @Override
    public ExchangeType getExchangeType() {
        return ExchangeType.HTX;
    }

    @Cacheable(cacheNames = "ex-instruments", key = "'HTX'", sync = true)
    public List<HtxContractItem> contracts() {
        String url = cfg.getBaseUrl() + "/linear-swap-api/v1/swap_contract_info";
        HtxResp<List<HtxContractItem>> resp =
                http.get(url, cfg.getTimeout(), new TypeReference<>() {
                });
        require(resp != null && "ok".equalsIgnoreCase(resp.status()) && resp.data() != null,
                () -> "HTX instruments error: " + (resp != null ? resp.status() : "null"));
        return resp.data();
    }

    @Cacheable(cacheNames = "ex-tickers", key = "'HTX'", sync = true)
    public Map<String, HtxBatchResp.Tick> tickers() {
        String url = cfg.getBaseUrl() + "/linear-swap-ex/market/detail/batch_merged";
        HtxBatchResp resp = http.get(url, cfg.getTimeout(), HtxBatchResp.class);
        require(resp != null && "ok".equalsIgnoreCase(resp.status()) && resp.ticks() != null,
                () -> "HTX batch tickers error: " + (resp != null ? resp.status() : "null"));
        return indexByCanonical(resp.ticks(), HtxBatchResp.Tick::contractCode);
    }

    @Cacheable(cacheNames = "ex-funding", key = "'HTX'", sync = true)
    public Map<String, HtxFundingItem> funding() {
        String url = cfg.getBaseUrl() + "/linear-swap-api/v1/swap_batch_funding_rate";
        HtxResp<List<HtxFundingItem>> resp =
                http.get(url, cfg.getTimeout(), new TypeReference<>() {
                });
        require(resp != null && "ok".equalsIgnoreCase(resp.status()) && resp.data() != null,
                () -> "HTX batch funding error: " + (resp != null ? resp.status() : "null"));
        return indexByCanonical(resp.data(), HtxFundingItem::contractCode);
    }
}