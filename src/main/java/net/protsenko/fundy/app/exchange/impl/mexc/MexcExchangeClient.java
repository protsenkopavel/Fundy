package net.protsenko.fundy.app.exchange.impl.mexc;

import net.protsenko.fundy.app.dto.FundingRateData;
import net.protsenko.fundy.app.dto.InstrumentType;
import net.protsenko.fundy.app.dto.TickerData;
import net.protsenko.fundy.app.dto.TradingInstrument;
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

    private volatile Map<String, TradingInstrument> symbolIndex;

    public MexcExchangeClient(MexcConfig config) {
        super(config);
    }

    @Override
    public ExchangeType getExchangeType() {
        return ExchangeType.MEXC;
    }

    @Override
    protected List<TradingInstrument> fetchAvailableInstruments() {
        String url = config.getBaseUrl() + "/api/v1/contract/detail";
        HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();

        MexcInstrumentsResponse resp = sendRequest(req, MexcInstrumentsResponse.class);
        if (resp == null || resp.code() != 0 || resp.data() == null) {
            throw new ExchangeException("MEXC instruments error: " + (resp != null ? resp.msg() : "null response"));
        }

        List<TradingInstrument> list = resp.data().stream()
                .filter(i -> i.state() == 0)
                .map(i -> new TradingInstrument(
                        i.baseCoin(),
                        i.quoteCoin(),
                        InstrumentType.PERPETUAL,
                        i.symbol()
                ))
                .toList();

        symbolIndex = list.stream()
                .collect(Collectors.toUnmodifiableMap(TradingInstrument::nativeSymbol, Function.identity()));

        return list;
    }

    @Override
    public TickerData getTicker(TradingInstrument instrument) {
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
                d(i.lastPrice()),
                d(i.bid1Price()),
                d(i.ask1Price()),
                d(i.high24Price()),
                d(i.low24Price()),
                d(i.volume24()),
                System.currentTimeMillis()
        );
    }

    @Override
    public FundingRateData getFundingRate(TradingInstrument instrument) {
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
            Map<String, TradingInstrument> dict = symbolIndex();
            return resp.data().stream()
                    .map(item -> {
                        TradingInstrument inst = dict.get(item.symbol());
                        if (inst == null) return null;
                        return new FundingRateData(
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

    private Map<String, TradingInstrument> symbolIndex() {
        Map<String, TradingInstrument> local = symbolIndex;
        if (local == null) {
            local = getAvailableInstruments().stream()
                    .collect(Collectors.toUnmodifiableMap(TradingInstrument::nativeSymbol, Function.identity()));
            symbolIndex = local;
        }
        return local;
    }

    private String symbol(TradingInstrument instrument) {
        return instrument.nativeSymbol() != null
                ? instrument.nativeSymbol()
                : instrument.baseAsset() + "_" + instrument.quoteAsset();
    }
}
