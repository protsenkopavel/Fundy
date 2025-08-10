package net.protsenko.fundy.app.exchange.impl.coinex;

import com.fasterxml.jackson.core.type.TypeReference;
import net.protsenko.fundy.app.dto.InstrumentType;
import net.protsenko.fundy.app.dto.rs.FundingRateData;
import net.protsenko.fundy.app.dto.rs.InstrumentData;
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

    public CoinexExchangeClient(CoinexConfig config) {
        super(config);
    }

    @Override
    public List<InstrumentData> getInstruments() {
        String url = config.getBaseUrl() + "/perpetual/v1/market/list";
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(config.getTimeout()))
                .GET()
                .build();

        CoinexResponse<List<CoinexContractItem>> resp = sendRequest(req, new TypeReference<>() {
        });

        if (resp == null || resp.code() != 0 || resp.data() == null) {
            throw new ExchangeException("CoinEx instruments error: " + (resp != null ? resp.message() : "null"));
        }

        return resp.data().stream()
                .filter(CoinexContractItem::available)
                .filter(i -> i.type() == 1)
                .map(this::toInstrument)
                .toList();
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
        String urlAll = config.getBaseUrl() + "/perpetual/v1/market/ticker/all";
        HttpRequest reqAll = HttpRequest.newBuilder()
                .uri(URI.create(urlAll))
                .timeout(Duration.ofSeconds(config.getTimeout()))
                .GET()
                .build();

        CoinexResponse<CoinexTickerAllData> respAll =
                sendRequest(reqAll, new TypeReference<>() {
                });

        if (respAll == null || respAll.code() != 0 || respAll.data() == null || respAll.data().ticker() == null) {
            throw new ExchangeException("CoinEx ticker/all error: " + (respAll != null ? respAll.message() : "null"));
        }

        Map<String, CoinexTickerItem> all = respAll.data().ticker();
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
        String urlAll = config.getBaseUrl() + "/perpetual/v1/market/ticker/all";
        HttpRequest reqAll = HttpRequest.newBuilder()
                .uri(URI.create(urlAll))
                .timeout(Duration.ofSeconds(config.getTimeout()))
                .GET()
                .build();

        CoinexResponse<CoinexTickerAllData> respAll = sendRequest(reqAll, new TypeReference<>() {
        });

        if (respAll == null || respAll.code() != 0 || respAll.data() == null || respAll.data().ticker() == null) {
            throw new ExchangeException("CoinEx ticker/all error: " + (respAll != null ? respAll.message() : "null"));
        }

        String symbol = ensureSymbol(instrument);
        CoinexTickerItem t = respAll.data().ticker().get(symbol);
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
    public List<FundingRateData> getFundingRates(List<InstrumentData> instruments) {
        String urlAll = config.getBaseUrl() + "/perpetual/v1/market/ticker/all";
        HttpRequest reqAll = HttpRequest.newBuilder()
                .uri(URI.create(urlAll))
                .timeout(Duration.ofSeconds(config.getTimeout()))
                .GET()
                .build();

        CoinexResponse<CoinexTickerAllData> respAll = sendRequest(reqAll, new TypeReference<>() {
        });

        if (respAll == null || respAll.code() != 0 || respAll.data() == null || respAll.data().ticker() == null) {
            throw new ExchangeException("CoinEx ticker/all error: " + (respAll != null ? respAll.message() : "null"));
        }

        Map<String, CoinexTickerItem> all = respAll.data().ticker();

        Map<String, InstrumentData> requested = instruments.stream()
                .collect(Collectors.toMap(this::ensureSymbol, Function.identity(), (a, b) -> a));

        return all.entrySet().stream()
                .filter(e -> requested.containsKey(e.getKey()))
                .map(e -> {
                    InstrumentData inst = requested.get(e.getKey());
                    CoinexTickerItem t = e.getValue();
                    return new FundingRateData(
                            getExchangeType(),
                            inst,
                            bd(t.fundingRateLast()),
                            calcNextFundingMs(t.fundingTime())
                    );
                })
                .toList();
    }

    @Override
    public ExchangeType getExchangeType() {
        return ExchangeType.COINEX;
    }

    @Override
    public Boolean isEnabled() {
        return config.isEnabled();
    }

    private long calcNextFundingMs(long fundingTimeField) {
        long now = System.currentTimeMillis();
        if (fundingTimeField <= 0) return now;

        long millis = fundingTimeField < 3600
                ? fundingTimeField * 60_000L
                : fundingTimeField * 1000L;

        return now + millis;
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

    private String ensureSymbol(InstrumentData inst) {
        return inst.nativeSymbol() != null ? inst.nativeSymbol()
                : inst.baseAsset().toUpperCase() + inst.quoteAsset().toUpperCase();
    }
}