package net.protsenko.fundy.app.exchange.impl.coinex;

import com.fasterxml.jackson.core.type.TypeReference;
import net.protsenko.fundy.app.dto.rs.FundingRateData;
import net.protsenko.fundy.app.dto.rs.InstrumentData;
import net.protsenko.fundy.app.dto.InstrumentType;
import net.protsenko.fundy.app.dto.rs.TickerData;
import net.protsenko.fundy.app.exception.ExchangeException;
import net.protsenko.fundy.app.exchange.AbstractExchangeClient;
import net.protsenko.fundy.app.exchange.ExchangeType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import static net.protsenko.fundy.app.utils.ExchangeUtils.bd;

@Component
public class CoinexExchangeClient extends AbstractExchangeClient<CoinexConfig> {

    private volatile Map<String, InstrumentData> symbolIndex;
    private volatile Map<String, CoinexContractItem> contractMeta;

    public CoinexExchangeClient(CoinexConfig config) {
        super(config);
    }

    private long calcNextFundingMs(long fundingTimeField) {
        long now = System.currentTimeMillis();
        if (fundingTimeField <= 0) return now;

        long millis = fundingTimeField < 3600
                ? fundingTimeField * 60_000L
                : fundingTimeField * 1000L;

        return now + millis;
    }

    @Override
    public ExchangeType getExchangeType() {
        return ExchangeType.COINEX;
    }

    @Override
    protected List<InstrumentData> fetchAvailableInstruments() {
        String url = config.getBaseUrl() + "/perpetual/v1/market/list";
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(config.getTimeout()))
                .GET()
                .build();

        CoinexResponse<List<CoinexContractItem>> resp =
                sendRequest(req, new TypeReference<>() {
                });

        if (resp == null || resp.code() != 0 || resp.data() == null) {
            throw new ExchangeException("CoinEx instruments error: " + (resp != null ? resp.message() : "null"));
        }

        List<CoinexContractItem> list = resp.data();

        List<InstrumentData> instruments = list.stream()
                .filter(CoinexContractItem::available)
                .filter(i -> i.type() == 1)
                .map(this::toInstrument)
                .toList();

        symbolIndex = instruments.stream()
                .collect(Collectors.toUnmodifiableMap(InstrumentData::nativeSymbol, Function.identity()));

        contractMeta = list.stream()
                .collect(Collectors.toUnmodifiableMap(CoinexContractItem::name, Function.identity()));

        return instruments;
    }

    private InstrumentData toInstrument(CoinexContractItem c) {
        return new InstrumentData(
                c.stock(),
                c.money(),
                InstrumentType.PERPETUAL,
                c.name(),
                getExchangeType()
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

    @Override
    public TickerData getTicker(InstrumentData instrument) {
        String symbol = ensureSymbol(instrument);
        String url = config.getBaseUrl() + "/perpetual/v1/market/ticker?market=" + symbol;

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(config.getTimeout()))
                .GET()
                .build();

        CoinexResponse<CoinexTickerSingleData> resp =
                sendRequest(req, new TypeReference<>() {
                });

        if (resp == null || resp.code() != 0 || resp.data() == null || resp.data().ticker() == null) {
            throw new ExchangeException("CoinEx ticker error: " + (resp != null ? resp.message() : "null"));
        }

        CoinexTickerItem t = resp.data().ticker();

        return new TickerData(
                instrument,
                bd(t.last()),
                bd(t.buy()),
                bd(t.sell()),
                bd(t.high()),
                bd(t.low()),
                bd(t.vol()),
                System.currentTimeMillis()
        );
    }

    @Override
    public List<TickerData> getTickers(List<InstrumentData> instruments) {
        Map<String, CoinexTickerItem> all = tickerAllMap();
        Map<String, InstrumentData> dict = symbolIndex();
        long now = System.currentTimeMillis();

        return instruments.stream()
                .map(inst -> {
                    CoinexTickerItem t = all.get(ensureSymbol(inst));
                    if (t == null) return null;
                    return new TickerData(
                            inst,
                            bd(t.last()),
                            bd(t.buy()),
                            bd(t.sell()),
                            bd(t.high()),
                            bd(t.low()),
                            bd(t.vol()),
                            now
                    );
                })
                .filter(Objects::nonNull)
                .toList();
    }

    @Override
    public FundingRateData getFundingRate(InstrumentData instrument) {
        String symbol = ensureSymbol(instrument);
        CoinexTickerItem t = tickerAllMap().get(symbol);
        if (t == null) {
            throw new ExchangeException("CoinEx funding: ticker not found for " + symbol);
        }

        BigDecimal rate = bd(t.fundingRateLast());
        long nextMs = calcNextFundingMs(t.fundingTime());

        return new FundingRateData(
                getExchangeType(),
                instrument,
                rate,
                nextMs
        );
    }

    @Override
    public List<FundingRateData> getAllFundingRates() {
        Map<String, CoinexTickerItem> map = tickerAllMap();
        Map<String, InstrumentData> dict = symbolIndex();

        return map.entrySet().stream()
                .map(e -> {
                    InstrumentData inst = dict.get(e.getKey());
                    if (inst == null) return null;
                    CoinexTickerItem t = e.getValue();
                    return new FundingRateData(
                            getExchangeType(),
                            inst,
                            bd(t.fundingRateLast()),
                            calcNextFundingMs(t.fundingTime())
                    );
                })
                .filter(Objects::nonNull)
                .toList();
    }

    private String ensureSymbol(InstrumentData inst) {
        return inst.nativeSymbol() != null ? inst.nativeSymbol()
                : inst.baseAsset().toUpperCase() + inst.quoteAsset().toUpperCase();
    }

    private Map<String, CoinexTickerItem> tickerAllMap() {
        String url = config.getBaseUrl() + "/perpetual/v1/market/ticker/all";
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(config.getTimeout()))
                .GET()
                .build();

        CoinexResponse<CoinexTickerAllData> resp =
                sendRequest(req, new TypeReference<>() {
                });

        if (resp == null || resp.code() != 0 || resp.data() == null || resp.data().ticker() == null) {
            throw new ExchangeException("CoinEx ticker/all error: " + (resp != null ? resp.message() : "null"));
        }
        return resp.data().ticker();
    }
}