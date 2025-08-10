package net.protsenko.fundy.app.exchange.impl.bitget;

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
public class BitgetExchangeClient extends AbstractExchangeClient<BitgetConfig> {

    private static final long EIGHT_HOURS_MS = Duration.ofHours(8).toMillis();
    private volatile long nextFundingGlobalMs = -1L;

    public BitgetExchangeClient(BitgetConfig config) {
        super(config);
    }

    @Override
    public List<InstrumentData> getInstruments() {
        String url = config.getBaseUrl()
                + "/api/mix/v1/market/contracts?productType=" + config.getProductType();
        HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();

        BitgetResponse<List<BitgetContractItem>> resp = sendRequest(
                req, new TypeReference<>() {
                }
        );

        if (resp == null || !"00000".equals(resp.code()) || resp.data() == null) {
            throw new ExchangeException("Bitget instruments error: " + (resp != null ? resp.msg() : "null response"));
        }

        return resp.data().stream()
                .filter(i -> "normal".equalsIgnoreCase(i.symbolStatus()))
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
        String url = config.getBaseUrl() + "/api/mix/v1/market/ticker?symbol=" + symbol;

        HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
        BitgetResponse<BitgetTickerItem> resp = sendRequest(
                req, new TypeReference<>() {
                }
        );

        if (resp == null || !"00000".equals(resp.code()) || resp.data() == null) {
            throw new ExchangeException("Bitget ticker error: " + (resp != null ? resp.msg() : "null response"));
        }

        BitgetTickerItem t = resp.data();
        return new TickerData(
                instrument,
                bd(t.last()),
                bd(t.bestBid()),
                bd(t.bestAsk()),
                bd(t.high24h()),
                bd(t.low24h()),
                bd(t.baseVolume()),
                System.currentTimeMillis()
        );
    }

    @Override
    public List<TickerData> getTickers(List<InstrumentData> instruments) {
        String url = config.getBaseUrl()
                + "/api/mix/v1/market/tickers?productType=" + config.getProductType();
        HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();

        BitgetResponse<List<BitgetTickerItem>> resp = sendRequest(
                req, new TypeReference<>() {
                }
        );

        if (resp == null || !"00000".equals(resp.code()) || resp.data() == null) {
            throw new ExchangeException("Bitget tickers error: "
                    + (resp != null ? resp.msg() : "null response"));
        }

        Map<String, BitgetTickerItem> bySymbol = resp.data().stream()
                .collect(Collectors.toMap(
                        BitgetTickerItem::symbol,
                        Function.identity(),
                        (a, b) -> a
                ));
        long now = System.currentTimeMillis();

        return instruments.stream()
                .map(inst -> {
                    BitgetTickerItem t = bySymbol.get(ensureSymbol(inst));
                    if (t == null) return null;
                    return new TickerData(
                            inst,
                            bd(t.last()),
                            bd(t.bestBid()),
                            bd(t.bestAsk()),
                            bd(t.high24h()),
                            bd(t.low24h()),
                            bd(t.baseVolume()),
                            now
                    );
                })
                .filter(Objects::nonNull)
                .toList();
    }

    @Override
    public FundingRateData getFundingRate(InstrumentData instrument) {
        String symbol = ensureSymbol(instrument);

        String url = config.getBaseUrl() + "/api/mix/v1/market/current-fundRate?symbol=" + symbol;
        HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
        BitgetResponse<BitgetFundingItem> resp = sendRequest(
                req, new TypeReference<>() {
                }
        );

        if (resp == null || !"00000".equals(resp.code()) || resp.data() == null) {
            throw new ExchangeException("Bitget funding error: " + (resp != null ? resp.msg() : "null response"));
        }

        BigDecimal rate = bd(resp.data().fundingRate());
        long next = nextFundingTimeGlobal();

        return new FundingRateData(
                getExchangeType(),
                instrument,
                rate,
                next
        );
    }

    @Override
    public List<FundingRateData> getFundingRates(List<InstrumentData> instruments) {
        String url = config.getBaseUrl()
                + "/api/mix/v1/market/tickers?productType=" + config.getProductType();
        HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();

        BitgetResponse<List<BitgetTickerItem>> resp = sendRequest(
                req, new TypeReference<>() {
                }
        );

        if (resp == null || !"00000".equals(resp.code()) || resp.data() == null) {
            throw new ExchangeException("Bitget tickers error: " + (resp != null ? resp.msg() : "null response"));
        }

        long next = nextFundingTimeGlobal();

        Map<String, InstrumentData> requested = instruments.stream()
                .collect(Collectors.toMap(
                        this::ensureSymbol,
                        Function.identity(),
                        (a, b) -> a
                ));

        return resp.data().stream()
                .filter(t -> requested.containsKey(t.symbol()))
                .map(t -> new FundingRateData(
                        getExchangeType(),
                        requested.get(t.symbol()),
                        bd(t.fundingRate()),
                        next
                ))
                .toList();
    }

    @Override
    public ExchangeType getExchangeType() {
        return ExchangeType.BITGET;
    }

    @Override
    public Boolean isEnabled() {
        return config.isEnabled();
    }

    private String ensureSymbol(InstrumentData instrument) {
        if (instrument.nativeSymbol() != null) return instrument.nativeSymbol();
        return instrument.baseAsset() + instrument.quoteAsset() + "_" + config.getProductType().toUpperCase();
    }

    private long nextFundingTimeGlobal() {
        long cached = nextFundingGlobalMs;
        long now = System.currentTimeMillis();
        if (cached > now) return cached;
        long next = ((now / EIGHT_HOURS_MS) + 1) * EIGHT_HOURS_MS;
        nextFundingGlobalMs = next;
        return next;
    }
}
