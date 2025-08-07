package net.protsenko.fundy.app.exchange.impl.bybit;

import net.protsenko.fundy.app.dto.*;
import net.protsenko.fundy.app.dto.rs.FundingRateData;
import net.protsenko.fundy.app.dto.rs.InstrumentData;
import net.protsenko.fundy.app.dto.rs.TickerData;
import net.protsenko.fundy.app.exception.ExchangeException;
import net.protsenko.fundy.app.exchange.AbstractExchangeClient;
import net.protsenko.fundy.app.exchange.ExchangeType;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import static net.protsenko.fundy.app.utils.ExchangeUtils.bd;
import static net.protsenko.fundy.app.utils.ExchangeUtils.l;


@Component
public class BybitExchangeClient extends AbstractExchangeClient<BybitConfig> {

    private volatile Map<String, InstrumentData> symbolIndex;

    public BybitExchangeClient(BybitConfig config) {
        super(config);
    }

    @Override
    public ExchangeType getExchangeType() {
        return ExchangeType.BYBIT;
    }

    @Override
    protected List<InstrumentData> fetchAvailableInstruments() {
        String url = config.getBaseUrl() + "/v5/market/instruments-info?category=linear";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();

        BybitInstrumentsResponse response = sendRequest(request, BybitInstrumentsResponse.class);

        if (response == null || response.retCode() != 0 || response.result() == null) {
            throw new ExchangeException("Bybit instruments error: " + (response != null ? response.retMsg() : "null response"));
        }

        List<InstrumentData> res = new ArrayList<>();
        for (BybitInstrumentItem item : response.result().list()) {
            if (!"Trading".equalsIgnoreCase(item.status())) continue;
            res.add(new InstrumentData(
                    item.baseCoin(),
                    item.quoteCoin(),
                    InstrumentType.PERPETUAL,
                    item.symbol(),
                    getExchangeType()
            ));
        }
        this.symbolIndex = res.stream()
                .collect(Collectors.toUnmodifiableMap(InstrumentData::nativeSymbol, Function.identity()));
        return res;
    }

    @Override
    public TickerData getTicker(InstrumentData instrument) {
        String symbol = instrument.nativeSymbol() != null ? instrument.nativeSymbol()
                : instrument.baseAsset() + instrument.quoteAsset();
        String url = config.getBaseUrl() + "/v5/market/tickers?category=linear&symbol=" + symbol;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();

        BybitTickerResponse response = sendRequest(request, BybitTickerResponse.class);

        if (response == null || response.retCode() != 0 || response.result() == null || response.result().list().isEmpty()) {
            throw new ExchangeException("Bybit ticker error for " + symbol + ": " +
                    (response != null ? response.retMsg() : "null response"));
        }

        BybitTickerItem item = response.result().list().getFirst();
        return new TickerData(
                instrument,
                bd(item.lastPrice()),
                bd(item.bid1Price()),
                bd(item.ask1Price()),
                bd(item.highPrice24h()),
                bd(item.lowPrice24h()),
                bd(item.volume24h()),
                System.currentTimeMillis()
        );
    }

    @Override
    public List<TickerData> getTickers(List<InstrumentData> instruments) {
        String url = config.getBaseUrl() + "/v5/market/tickers?category=linear";
        HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();

        BybitTickerResponse resp = sendRequest(req, BybitTickerResponse.class);
        if (resp == null || resp.retCode() != 0 || resp.result() == null)
            throw new ExchangeException("Bybit tickers error: " +
                    (resp != null ? resp.retMsg() : "null"));

        Map<String, BybitTickerItem> bySymbol = resp.result().list().stream()
                .collect(Collectors.toMap(BybitTickerItem::symbol, Function.identity()));

        return instruments.stream()
                .map(inst -> {
                    BybitTickerItem i = bySymbol.get(inst.nativeSymbol());
                    if (i == null) return null;
                    return new TickerData(
                            inst,
                            bd(i.lastPrice()),
                            bd(i.bid1Price()),
                            bd(i.ask1Price()),
                            bd(i.highPrice24h()),
                            bd(i.lowPrice24h()),
                            bd(i.volume24h()),
                            System.currentTimeMillis()
                    );
                })
                .filter(Objects::nonNull)
                .toList();
    }

    public List<FundingRateData> getAllFundingRates() {
        String url = config.getBaseUrl() + "/v5/market/tickers?category=linear";
        HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();

        BybitTickerResponse resp = sendRequest(req, BybitTickerResponse.class);
        if (resp == null || resp.retCode() != 0 || resp.result() == null) {
            throw new ExchangeException("Bybit tickers error: " + (resp != null ? resp.retMsg() : "null response"));
        }

        Map<String, InstrumentData> dict = symbolIndex();

        return resp.result().list().stream()
                .map(item -> mapFunding(item, dict))
                .filter(Objects::nonNull)
                .toList();
    }

    public FundingRateData getFundingRate(InstrumentData instrument) {
        String symbol = instrument.nativeSymbol() != null ? instrument.nativeSymbol()
                : instrument.baseAsset() + instrument.quoteAsset();
        String url = config.getBaseUrl() + "/v5/market/tickers?category=linear&symbol=" + symbol;

        HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
        BybitTickerResponse resp = sendRequest(req, BybitTickerResponse.class);

        if (resp == null || resp.retCode() != 0 || resp.result() == null || resp.result().list().isEmpty()) {
            throw new ExchangeException("Bybit funding error for " + symbol + ": " +
                    (resp != null ? resp.retMsg() : "null response"));
        }

        BybitTickerItem i = resp.result().list().get(0);
        return new FundingRateData(
                getExchangeType(),
                instrument,
                bd(i.fundingRate()),
                l(i.nextFundingTime())
        );
    }

    private Map<String, InstrumentData> symbolIndex() {
        Map<String, InstrumentData> local = symbolIndex;
        if (local == null) {
            local = getAvailableInstruments().stream()
                    .collect(Collectors.toUnmodifiableMap(InstrumentData::nativeSymbol, Function.identity()));
            symbolIndex = local;
        }
        return local;
    }

    private FundingRateData mapFunding(BybitTickerItem item, Map<String, InstrumentData> dict) {
        InstrumentData inst = dict.get(item.symbol());
        if (inst == null) return null;
        return new FundingRateData(
                getExchangeType(),
                inst,
                bd(item.fundingRate()),
                l(item.nextFundingTime())
        );
    }
}
