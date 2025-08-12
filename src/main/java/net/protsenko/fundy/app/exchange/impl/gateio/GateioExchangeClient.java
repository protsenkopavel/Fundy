package net.protsenko.fundy.app.exchange.impl.gateio;


import com.fasterxml.jackson.core.type.TypeReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.protsenko.fundy.app.dto.InstrumentType;
import net.protsenko.fundy.app.dto.rs.FundingRateData;
import net.protsenko.fundy.app.dto.rs.InstrumentData;
import net.protsenko.fundy.app.dto.rs.TickerData;
import net.protsenko.fundy.app.exception.ExchangeException;
import net.protsenko.fundy.app.exchange.ExchangeClient;
import net.protsenko.fundy.app.exchange.ExchangeType;
import net.protsenko.fundy.app.exchange.support.ExchangeMappingSupport;
import net.protsenko.fundy.app.props.GateioConfig;
import net.protsenko.fundy.app.utils.HttpExecutor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class GateioExchangeClient implements ExchangeClient, ExchangeMappingSupport {

    private final HttpExecutor httpExecutor;
    private final GateioConfig config;

    @Override
    public List<InstrumentData> getInstruments() {
        String url = config.getBaseUrl() + "/api/v4/futures/" + config.getSettle() + "/contracts";
        List<GateioContractItem> resp = httpExecutor.get(url, config.getTimeout(), new TypeReference<>() {
        });

        require(resp != null, () -> "GateIO contracts: null response");

        return resp.stream()
                .filter(c -> "trading".equalsIgnoreCase(c.status()))
                .map(c -> {
                    String nativeSymbol = c.name();
                    String[] p = nativeSymbol.split("_");
                    String base = p.length > 0 ? p[0] : "";
                    String quote = p.length > 1 ? p[1] : config.getSettle().toUpperCase();
                    return instrument(base, quote, InstrumentType.PERPETUAL, nativeSymbol);
                })
                .toList();
    }

    @Override
    public TickerData getTicker(InstrumentData instrument) {
        String url = config.getBaseUrl() + "/api/v4/futures/" + config.getSettle() + "/tickers";
        List<GateioTickerItem> resp = httpExecutor.get(url, config.getTimeout(), new TypeReference<>() {
        });

        require(resp != null, () -> "GateIO tickers: null response");

        Map<String, GateioTickerItem> byCanonical = indexByCanonical(resp, GateioTickerItem::contract);

        return mapTickersByCanonical(
                List.of(instrument),
                byCanonical,
                (inst, t) -> ticker(
                        inst,
                        t.last(),
                        t.highestBid(),
                        t.lowestAsk(),
                        t.high24h(),
                        t.low24h(),
                        t.volume24h()
                )
        ).stream().findFirst().orElseThrow(() ->
                new ExchangeException("[" + getExchangeType() + "] ticker not found for "
                        + instrument.baseAsset() + "/" + instrument.quoteAsset()));
    }

    @Override
    public List<TickerData> getTickers(List<InstrumentData> instruments) {
        String url = config.getBaseUrl() + "/api/v4/futures/" + config.getSettle() + "/tickers";
        List<GateioTickerItem> resp = httpExecutor.get(url, config.getTimeout(), new TypeReference<>() {
        });

        require(resp != null, () -> "GateIO tickers: null response");

        Map<String, GateioTickerItem> byCanonical = indexByCanonical(resp, GateioTickerItem::contract);

        return mapTickersByCanonical(
                instruments,
                byCanonical,
                (inst, t) -> ticker(
                        inst,
                        t.last(),
                        t.highestBid(),
                        t.lowestAsk(),
                        t.high24h(),
                        t.low24h(),
                        t.volume24h()
                )
        );
    }

    @Override
    public FundingRateData getFundingRate(InstrumentData instrument) {
        String url = config.getBaseUrl() + "/api/v4/futures/" + config.getSettle() + "/contracts";
        List<GateioContractItem> resp = httpExecutor.get(url, config.getTimeout(), new TypeReference<>() {
        });

        require(resp != null, () -> "GateIO contracts: null response");

        Map<String, GateioContractItem> byCanonical = indexByCanonical(resp, GateioContractItem::name);

        return mapFundingByCanonical(
                List.of(instrument),
                byCanonical,
                (inst, c) -> funding(inst, c.fundingRate(), c.fundingNextApply() * 1000L)
        ).stream().findFirst().orElseThrow(() ->
                new ExchangeException("[" + getExchangeType() + "] funding not found for "
                        + instrument.baseAsset() + "/" + instrument.quoteAsset()));
    }

    @Override
    public List<FundingRateData> getFundingRates(List<InstrumentData> instruments) {
        String url = config.getBaseUrl() + "/api/v4/futures/" + config.getSettle() + "/contracts";
        List<GateioContractItem> resp = httpExecutor.get(url, config.getTimeout(), new TypeReference<>() {
        });

        require(resp != null, () -> "GateIO contracts: null response");

        Map<String, GateioContractItem> byCanonical = indexByCanonical(resp, GateioContractItem::name);

        return mapFundingByCanonical(
                instruments,
                byCanonical,
                (inst, c) -> funding(inst, c.fundingRate(), c.fundingNextApply() * 1000L)
        );
    }

    @Override
    public ExchangeType getExchangeType() {
        return ExchangeType.GATEIO;
    }

    @Override
    public Boolean isEnabled() {
        return config.isEnabled();
    }
}
