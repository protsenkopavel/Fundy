package net.protsenko.fundy.app.exchange.impl.bybit;

import net.protsenko.fundy.app.dto.InstrumentType;
import net.protsenko.fundy.app.dto.rs.FundingRateData;
import net.protsenko.fundy.app.dto.rs.InstrumentData;
import net.protsenko.fundy.app.dto.rs.TickerData;
import net.protsenko.fundy.app.exception.ExchangeException;
import net.protsenko.fundy.app.exchange.AbstractExchangeClient;
import net.protsenko.fundy.app.exchange.ExchangeType;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpRequest;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import static net.protsenko.fundy.app.utils.ExchangeUtils.bd;
import static net.protsenko.fundy.app.utils.ExchangeUtils.l;


@Component
public class BybitExchangeClient extends AbstractExchangeClient<BybitConfig> {

    public BybitExchangeClient(BybitConfig config) {
        super(config);
    }

    @Override
    public List<InstrumentData> getInstruments() {
        String url = config.getBaseUrl() + "/v5/market/instruments-info?category=linear";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();

        BybitInstrumentsResponse response = sendRequest(request, BybitInstrumentsResponse.class);

        if (response == null || response.retCode() != 0 || response.result() == null) {
            throw new ExchangeException("Bybit instruments error: " + (response != null ? response.retMsg() : "null response"));
        }

        return response.result().list().stream()
                .filter(item -> !"Trading".equalsIgnoreCase(item.status()))
                .map(i -> new InstrumentData(
                        i.baseCoin(),
                        i.quoteCoin(),
                        InstrumentType.PERPETUAL,
                        i.symbol(),
                        getExchangeType()
                ))
                .toList();
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

    @Override
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

        BybitTickerItem i = resp.result().list().getFirst();
        return new FundingRateData(
                getExchangeType(),
                instrument,
                bd(i.fundingRate()),
                l(i.nextFundingTime())
        );
    }

    @Override
    public List<FundingRateData> getFundingRates(List<InstrumentData> instruments) {
        String url = config.getBaseUrl() + "/v5/market/tickers?category=linear";
        HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();

        BybitTickerResponse resp = sendRequest(req, BybitTickerResponse.class);
        if (resp == null || resp.retCode() != 0 || resp.result() == null) {
            throw new ExchangeException("Bybit tickers error: " + (resp != null ? resp.retMsg() : "null response"));
        }

        Map<String, InstrumentData> requested = instruments.stream()
                .collect(Collectors.toMap(this::ensureSymbol, Function.identity(), (a, b) -> a));

        return resp.result().list().stream()
                .filter(item -> requested.containsKey(item.symbol()))
                .map(item -> new FundingRateData(
                        getExchangeType(),
                        requested.get(item.symbol()),
                        bd(item.fundingRate()),
                        l(item.nextFundingTime())
                ))
                .toList();
    }

    @Override
    public ExchangeType getExchangeType() {
        return ExchangeType.BYBIT;
    }

    @Override
    public Boolean isEnabled() {
        return config.isEnabled();
    }

    private String ensureSymbol(InstrumentData instrument) {
        if (instrument.nativeSymbol() != null) return instrument.nativeSymbol();
        return instrument.baseAsset() + instrument.quoteAsset();
    }
}
