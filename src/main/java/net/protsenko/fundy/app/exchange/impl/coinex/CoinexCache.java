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
import java.util.stream.Collectors;

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

    @Cacheable(cacheNames = "ex-funding-meta", key = "'COINEX'", sync = true)
    public Map<String, CoinexFundingMeta> fundingMeta() {
        String url = cfg.getBaseUrl() + "/v2/futures/funding-rate";
        CoinexResponse<List<CoinexFundingMeta>> resp =
                http.get(url, cfg.getTimeout(), new TypeReference<>() {
                });
        require(resp != null && resp.code() == 0 && resp.data() != null,
                () -> "CoinEx funding-rate error: " + (resp != null ? resp.message() : "null"));
        return indexByCanonical(resp.data(), CoinexFundingMeta::market);
    }

    @Cacheable(cacheNames = "ex-spot-instruments", key = "'COINEX'", sync = true)
    public Map<String, CoinexSpotInstrumentItem> spotInstruments() {
        String url = cfg.getBaseUrl() + "/v1/market/list";
        CoinexResponse<List<String>> resp =
                http.get(url, cfg.getTimeout(), new TypeReference<>() {
                });
        require(resp != null && resp.code() == 0 && resp.data() != null,
                () -> "CoinEx spot instruments error: " + (resp != null ? resp.message() : "null"));

        return resp.data().stream()
                .filter(market -> market.endsWith("USDT") || market.endsWith("USDC") || market.endsWith("USD"))
                .map(market -> {
                    String base = market.replaceAll("(USDT|USDC|USD)$", "");
                    String quote = market.endsWith("USDT") ? "USDT" :
                                   market.endsWith("USDC") ? "USDC" : "USD";

                    return new CoinexSpotInstrumentItem(
                            market, base, quote, true, false, false, 1,
                            "0", "0", "0", "0", "0", "0"
                    );
                })
                .collect(Collectors.toMap(
                        CoinexSpotInstrumentItem::name,
                        item -> item,
                        (existing, replacement) -> existing
                ));
    }

    @Cacheable(cacheNames = "ex-spot-tickers", key = "'COINEX'", sync = true)
    public Map<String, CoinexSpotTickerItem> spotTickers() {
        String url = cfg.getBaseUrl() + "/v1/market/ticker/all";
        CoinexResponse<CoinexTickerAllData> resp =
                http.get(url, cfg.getTimeout(), new TypeReference<>() {
                });
        require(resp != null && resp.code() == 0 && resp.data() != null && resp.data().ticker() != null,
                () -> "CoinEx spot tickers error: " + (resp != null ? resp.message() : "null"));
        return indexByCanonical(resp.data().ticker().entrySet().stream()
                .map(e -> new CoinexSpotTickerItem(e.getKey(), e.getValue().last(), e.getValue().buy(),
                        e.getValue().sell(), e.getValue().high(), e.getValue().low(), e.getValue().vol(),
                        e.getValue().buyAmount(), "0"))
                .toList(), CoinexSpotTickerItem::market);
    }
}