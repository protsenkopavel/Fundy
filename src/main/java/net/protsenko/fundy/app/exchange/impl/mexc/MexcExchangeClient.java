package net.protsenko.fundy.app.exchange.impl.mexc;

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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import static net.protsenko.fundy.app.utils.ExchangeUtils.*;

@Component
public class MexcExchangeClient extends AbstractExchangeClient<MexcConfig> {

    private volatile Map<String, InstrumentData> symbolIndex;

    public MexcExchangeClient(MexcConfig config) {
        super(config);
    }

    @Override
    public ExchangeType getExchangeType() {
        return ExchangeType.MEXC;
    }

    @Override
    protected List<InstrumentData> fetchAvailableInstruments() {
        String url = config.getBaseUrl() + "/api/v1/contract/detail";
        HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();

        MexcInstrumentsResponse resp = sendRequest(req, MexcInstrumentsResponse.class);
        if (resp == null || resp.code() != 0 || resp.data() == null) {
            throw new ExchangeException("MEXC instruments error: " + (resp != null ? resp.msg() : "null response"));
        }

        List<InstrumentData> list = resp.data().stream()
                .filter(i -> i.state() == 0)
                .map(i -> new InstrumentData(
                        i.baseCoin(),
                        i.quoteCoin(),
                        InstrumentType.PERPETUAL,
                        i.symbol(),
                        getExchangeType()
                ))
                .toList();

        symbolIndex = list.stream()
                .collect(Collectors.toUnmodifiableMap(InstrumentData::nativeSymbol, Function.identity()));

        return list;
    }

    @Override
    public TickerData getTicker(InstrumentData instrument) {
        String symbol = symbol(instrument);
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
                    MexcTickerItem t = bySymbol.get(symbol(inst));
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
        String symbol = symbol(instrument);
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
    public List<FundingRateData> getAllFundingRates() {
        String url = config.getBaseUrl() + "/api/v1/contract/funding_rate";
        HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();

        MexcFundingListResponse resp = sendRequest(req, MexcFundingListResponse.class);
        if (resp != null && resp.code() == 0 && resp.data() != null && !resp.data().isEmpty()) {
            Map<String, InstrumentData> dict = symbolIndex();
            return resp.data().stream()
                    .map(item -> {
                        InstrumentData inst = dict.get(item.symbol());
                        if (inst == null) return null;
                        return new FundingRateData(
                                getExchangeType(),
                                inst,
                                bd(item.fundingRate()),
                                l(item.fundingTime())
                        );
                    })
                    .filter(Objects::nonNull)
                    .toList();
        }

        return getAvailableInstruments().stream()
                .map(this::getFundingRate)
                .toList();
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

    private String symbol(InstrumentData instrument) {
        return instrument.nativeSymbol() != null
                ? instrument.nativeSymbol()
                : instrument.baseAsset() + "_" + instrument.quoteAsset();
    }
}
