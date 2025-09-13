package net.protsenko.fundy.app.exchange.impl.gateio;

import com.fasterxml.jackson.core.type.TypeReference;
import lombok.RequiredArgsConstructor;
import net.protsenko.fundy.app.exchange.ExchangeType;
import net.protsenko.fundy.app.exchange.support.ExchangeMappingSupport;
import net.protsenko.fundy.app.props.GateioConfig;
import net.protsenko.fundy.app.utils.HttpExecutor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class GateioCache implements ExchangeMappingSupport {

    private final HttpExecutor http;
    private final GateioConfig cfg;

    @Override
    public ExchangeType getExchangeType() {
        return ExchangeType.GATEIO;
    }

    @Cacheable(cacheNames = "ex-instruments", key = "'GATEIO'", sync = true)
    public Map<String, GateioContractItem> contracts() {
        String url = cfg.getBaseUrl() + "/api/v4/futures/" + cfg.getSettle() + "/contracts";
        List<GateioContractItem> resp = http.get(url, cfg.getTimeout(), new TypeReference<>() {
        });
        require(resp != null, () -> "GateIO contracts: null response");
        return indexByCanonical(resp, GateioContractItem::name);
    }

    @Cacheable(cacheNames = "ex-tickers", key = "'GATEIO'", sync = true)
    public Map<String, GateioTickerItem> tickers() {
        String url = cfg.getBaseUrl() + "/api/v4/futures/" + cfg.getSettle() + "/tickers";
        List<GateioTickerItem> resp = http.get(url, cfg.getTimeout(), new TypeReference<>() {
        });
        require(resp != null, () -> "GateIO tickers: null response");
        return indexByCanonical(resp, GateioTickerItem::contract);
    }

    @Cacheable(cacheNames = "ex-spot-instruments", key = "'GATEIO'", sync = true)
    public Map<String, GateioSpotInstrumentItem> spotInstruments() {
        String url = cfg.getBaseUrl() + "/api/v4/spot/currency_pairs";
        List<GateioSpotInstrumentItem> resp = http.get(url, cfg.getTimeout(), new TypeReference<>() {
        });
        require(resp != null, () -> "GateIO spot instruments: null response");
        return resp.stream()
                .filter(item -> "tradable".equals(item.tradeStatus()))
                .collect(Collectors.toMap(
                        GateioSpotInstrumentItem::id,
                        item -> item,
                        (existing, replacement) -> existing
                ));
    }

    @Cacheable(cacheNames = "ex-spot-tickers", key = "'GATEIO'", sync = true)
    public Map<String, GateioSpotTickerItem> spotTickers() {
        String url = cfg.getBaseUrl() + "/api/v4/spot/tickers";
        List<GateioSpotTickerItem> resp = http.get(url, cfg.getTimeout(), new TypeReference<>() {
        });
        require(resp != null, () -> "GateIO spot tickers: null response");
        return indexByCanonical(resp, GateioSpotTickerItem::currencyPair);
    }
}