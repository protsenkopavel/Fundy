package net.protsenko.fundy.app.exchange.impl.mexc;

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
public class MexcExchangeClient extends AbstractExchangeClient<MexcConfig> {

    public MexcExchangeClient(MexcConfig config) {
        super(config);
    }

    @Override
    public List<InstrumentData> getInstruments() {
        String url = config.getBaseUrl() + "/api/v1/contract/detail";
        HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();

        MexcInstrumentsResponse resp = sendRequest(req, MexcInstrumentsResponse.class);
        if (resp == null || resp.code() != 0 || resp.data() == null) {
            throw new ExchangeException("MEXC instruments error: " + (resp != null ? resp.msg() : "null response"));
        }

        return resp.data().stream()
                .filter(i -> i.state() == 0)
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
        String symbol = ensureSymbol(instrument);
        String url = config.getBaseUrl() + "/api/v1/contract/ticker?symbol=" + symbol;

        HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
        MexcTickerResponse resp = sendRequest(req, MexcTickerResponse.class);

        if (resp == null || resp.code() != 0 || resp.data() == null) {
            throw new ExchangeException("MEXC ticker error: " + (resp != null ? resp.msg() : "null response"));
        }

        MexcTickerItem i = resp.data();
        return new TickerData(
                instrument,
                bd(i.lastPrice()),
                bd(i.bid1Price()),
                bd(i.ask1Price()),
                bd(i.high24Price()),
                bd(i.low24Price()),
                bd(i.volume24()),
                System.currentTimeMillis()
        );
    }

    @Override
    public List<TickerData> getTickers(List<InstrumentData> instruments) {
        String url = config.getBaseUrl() + "/api/v1/contract/ticker";
        HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();

        MexcTickerListWrapper wr = sendRequest(req, MexcTickerListWrapper.class);
        if (wr == null || wr.code() != 0 || wr.data() == null) {
            throw new ExchangeException("MEXC tickers error: "
                    + (wr != null ? wr.msg() : "null"));
        }

        Map<String, MexcTickerItem> bySymbol = wr.data().stream()
                .collect(Collectors.toMap(MexcTickerItem::symbol, Function.identity()));

        long now = System.currentTimeMillis();

        return instruments.stream()
                .map(inst -> {
                    MexcTickerItem t = bySymbol.get(ensureSymbol(inst));
                    if (t == null) return null;
                    return new TickerData(
                            inst,
                            bd(t.lastPrice()),
                            bd(t.bid1Price()),
                            bd(t.ask1Price()),
                            bd(t.high24Price()),
                            bd(t.low24Price()),
                            bd(t.volume24()),
                            now
                    );
                })
                .filter(Objects::nonNull)
                .toList();
    }

    @Override
    public FundingRateData getFundingRate(InstrumentData instrument) {
        String symbol = ensureSymbol(instrument);
        String url = config.getBaseUrl() + "/api/v1/contract/funding_rate?symbol=" + symbol;

        HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
        MexcFundingListResponse resp = sendRequest(req, MexcFundingListResponse.class);

        if (resp == null || resp.code() != 0 || resp.data() == null || resp.data().isEmpty()) {
            throw new ExchangeException("MEXC funding error for " + symbol + ": " +
                    (resp != null ? resp.msg() : "null response"));
        }

        MexcFundingItem d = resp.data().stream()
                .filter(it -> symbol.equalsIgnoreCase(it.symbol()))
                .findFirst()
                .orElse(resp.data().getFirst());

        return new FundingRateData(
                getExchangeType(),
                instrument,
                bd(d.fundingRate()),
                l(d.fundingTime())
        );
    }

    @Override
    public List<FundingRateData> getFundingRates(List<InstrumentData> instruments) {
        String url = config.getBaseUrl() + "/api/v1/contract/funding_rate";
        HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();

        MexcFundingListResponse resp = sendRequest(req, MexcFundingListResponse.class);
        if (resp != null && resp.code() == 0 && resp.data() != null && !resp.data().isEmpty()) {
            Map<String, InstrumentData> requested = instruments.stream()
                    .collect(Collectors.toMap(this::ensureSymbol, Function.identity(), (a, b) -> a));

            return resp.data().stream()
                    .filter(item -> requested.containsKey(item.symbol()))
                    .map(item -> new FundingRateData(
                            getExchangeType(),
                            requested.get(item.symbol()),
                            bd(item.fundingRate()),
                            l(item.fundingTime())
                    ))
                    .toList();
        }

        return instruments.stream()
                .map(this::getFundingRate)
                .toList();
    }

    @Override
    public ExchangeType getExchangeType() {
        return ExchangeType.MEXC;
    }

    @Override
    public Boolean isEnabled() {
        return config.isEnabled();
    }

    private String ensureSymbol(InstrumentData instrument) {
        return instrument.nativeSymbol() != null
                ? instrument.nativeSymbol()
                : instrument.baseAsset() + "_" + instrument.quoteAsset();
    }
}
