package net.protsenko.fundy.app.exchange.impl.bybit;

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
import net.protsenko.fundy.app.props.BybitConfig;
import net.protsenko.fundy.app.utils.HttpExecutor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

import static net.protsenko.fundy.app.utils.ExchangeUtils.toLong;


@Slf4j
@Component
@RequiredArgsConstructor
public class BybitExchangeClient implements ExchangeClient, ExchangeMappingSupport {

    private final HttpExecutor httpExecutor;
    private final BybitConfig config;

    @Override
    public List<InstrumentData> getInstruments() {
        String url = config.getBaseUrl() + "/v5/market/instruments-info?category=linear";
        BybitInstrumentsResponse resp = httpExecutor.get(url, config.getTimeout(), BybitInstrumentsResponse.class);

        require(resp != null && resp.retCode() == 0 && resp.result() != null,
                () -> "Bybit instruments error: " + (resp != null ? resp.retMsg() : "null response"));

        return resp.result().list().stream()
                .filter(i -> "Trading".equalsIgnoreCase(i.status()))
                .map(i -> instrument(
                        i.baseCoin(),
                        i.quoteCoin(),
                        InstrumentType.PERPETUAL,
                        i.symbol()
                ))
                .toList();
    }

    @Override
    public TickerData getTicker(InstrumentData instrument) {
        String url = config.getBaseUrl() + "/v5/market/tickers?category=linear";
        BybitTickerResponse resp = httpExecutor.get(url, config.getTimeout(), BybitTickerResponse.class);

        require(resp != null && resp.retCode() == 0 && resp.result() != null,
                () -> "Bybit tickers error: " + (resp != null ? resp.retMsg() : "null response"));

        Map<String, BybitTickerItem> byCanonical = indexByCanonical(resp.result().list(), BybitTickerItem::symbol);

        return mapTickersByCanonical(
                List.of(instrument),
                byCanonical,
                (inst, t) -> ticker(
                        inst,
                        t.lastPrice(),
                        t.bid1Price(),
                        t.ask1Price(),
                        t.highPrice24h(),
                        t.lowPrice24h(),
                        t.volume24h()
                )
        ).stream().findFirst().orElseThrow(() ->
                new ExchangeException("[" + getExchangeType() + "] ticker not found for "
                        + instrument.baseAsset() + "/" + instrument.quoteAsset()));
    }

    @Override
    public List<TickerData> getTickers(List<InstrumentData> instruments) {
        String url = config.getBaseUrl() + "/v5/market/tickers?category=linear";
        BybitTickerResponse resp = httpExecutor.get(url, config.getTimeout(), BybitTickerResponse.class);

        require(resp != null && resp.retCode() == 0 && resp.result() != null,
                () -> "Bybit tickers error: " + (resp != null ? resp.retMsg() : "null response"));

        Map<String, BybitTickerItem> byCanonical = indexByCanonical(resp.result().list(), BybitTickerItem::symbol);

        return mapTickersByCanonical(
                instruments,
                byCanonical,
                (inst, t) -> ticker(
                        inst,
                        t.lastPrice(),
                        t.bid1Price(),
                        t.ask1Price(),
                        t.highPrice24h(),
                        t.lowPrice24h(),
                        t.volume24h()
                )
        );
    }

    @Override
    public FundingRateData getFundingRate(InstrumentData instrument) {
        String url = config.getBaseUrl() + "/v5/market/tickers?category=linear";
        BybitTickerResponse resp = httpExecutor.get(url, config.getTimeout(), BybitTickerResponse.class);

        require(resp != null && resp.retCode() == 0 && resp.result() != null,
                () -> "Bybit funding error: " + (resp != null ? resp.retMsg() : "null response"));

        Map<String, BybitTickerItem> byCanonical = indexByCanonical(resp.result().list(), BybitTickerItem::symbol);

        return mapFundingByCanonical(
                List.of(instrument),
                byCanonical,
                (inst, t) -> funding(inst, t.fundingRate(), toLong(t.nextFundingTime()))
        ).stream().findFirst().orElseThrow(() ->
                new ExchangeException("[" + getExchangeType() + "] funding not found for "
                        + instrument.baseAsset() + "/" + instrument.quoteAsset()));
    }

    @Override
    public List<FundingRateData> getFundingRates(List<InstrumentData> instruments) {
        String url = config.getBaseUrl() + "/v5/market/tickers?category=linear";
        BybitTickerResponse resp = httpExecutor.get(url, config.getTimeout(), BybitTickerResponse.class);

        require(resp != null && resp.retCode() == 0 && resp.result() != null,
                () -> "Bybit funding error: " + (resp != null ? resp.retMsg() : "null response"));

        Map<String, BybitTickerItem> byCanonical = indexByCanonical(resp.result().list(), BybitTickerItem::symbol);

        return mapFundingByCanonical(
                instruments,
                byCanonical,
                (inst, t) -> funding(inst, t.fundingRate(), toLong(t.nextFundingTime()))
        );
    }

    @Override
    public ExchangeType getExchangeType() {
        return ExchangeType.BYBIT;
    }

    @Override
    public Boolean isEnabled() {
        return config.isEnabled();
    }
}
