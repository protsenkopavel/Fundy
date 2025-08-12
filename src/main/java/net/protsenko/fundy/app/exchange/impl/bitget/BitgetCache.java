package net.protsenko.fundy.app.exchange.impl.bitget;

import com.fasterxml.jackson.core.type.TypeReference;
import lombok.RequiredArgsConstructor;
import net.protsenko.fundy.app.exchange.ExchangeType;
import net.protsenko.fundy.app.exchange.support.ExchangeMappingSupport;
import net.protsenko.fundy.app.props.BitgetConfig;
import net.protsenko.fundy.app.utils.HttpExecutor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class BitgetCache implements ExchangeMappingSupport {

    private final HttpExecutor http;
    private final BitgetConfig cfg;

    @Override
    public ExchangeType getExchangeType() {
        return ExchangeType.BITGET;
    }

    @Cacheable(cacheNames = "ex-instruments", key = "'BITGET'", sync = true)
    public List<BitgetContractItem> contracts() {
        String url = cfg.getBaseUrl() + "/api/mix/v1/market/contracts?productType=" + cfg.getProductType();
        BitgetResponse<List<BitgetContractItem>> resp = http.get(url, cfg.getTimeout(), new TypeReference<>() {
        });
        require(resp != null && "00000".equals(resp.code()) && resp.data() != null,
                () -> "Bitget instruments error: " + (resp != null ? resp.msg() : "null response"));
        return resp.data();
    }

    @Cacheable(cacheNames = "ex-tickers", key = "'BITGET'", sync = true)
    public Map<String, BitgetTickerItem> tickers() {
        String url = cfg.getBaseUrl() + "/api/mix/v1/market/tickers?productType=" + cfg.getProductType();
        BitgetResponse<List<BitgetTickerItem>> resp = http.get(url, cfg.getTimeout(), new TypeReference<>() {
        });
        require(resp != null && "00000".equals(resp.code()) && resp.data() != null,
                () -> "Bitget tickers error: " + (resp != null ? resp.msg() : "null response"));
        return indexByCanonical(resp.data(), BitgetTickerItem::symbol);
    }

    @Cacheable(cacheNames = "ex-funding-meta", key = "'BITGET'", sync = true)
    public Map<String, BitgetFundingMeta> fundingMeta() {
        String v2Type = mapToV2ProductType(cfg.getProductType());
        String url = cfg.getBaseUrl() + "/api/v2/mix/market/current-fund-rate?productType=" + v2Type;
        BitgetResponse<List<BitgetFundingMeta>> resp =
                http.get(url, cfg.getTimeout(), new TypeReference<>() {
                });
        require(resp != null && "00000".equals(resp.code()) && resp.data() != null,
                () -> "Bitget current-fund-rate error: " + (resp != null ? resp.msg() : "null response"));
        return indexByCanonical(resp.data(), BitgetFundingMeta::symbol);
    }

    private String mapToV2ProductType(String v1) {
        String t = v1 == null ? "" : v1.toLowerCase(Locale.ROOT).trim();
        return switch (t) {
            case "umcbl" -> "usdt-futures";
            case "dmcbl" -> "usdc-futures";
            case "cmcbl" -> "coin-futures";
            default -> "usdt-futures";
        };
    }
}
