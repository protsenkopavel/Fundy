package net.protsenko.fundy.app.exchange.impl.okx;

import com.fasterxml.jackson.core.type.TypeReference;
import net.protsenko.fundy.app.dto.FundingRateData;
import net.protsenko.fundy.app.dto.InstrumentType;
import net.protsenko.fundy.app.dto.TickerData;
import net.protsenko.fundy.app.dto.TradingInstrument;
import net.protsenko.fundy.app.exception.ExchangeException;
import net.protsenko.fundy.app.exchange.AbstractExchangeClient;
import net.protsenko.fundy.app.exchange.ExchangeType;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.stream.Collectors;

import static net.protsenko.fundy.app.utils.ExchangeUtils.*;

@Component
public class OkxExchangeClient extends AbstractExchangeClient<OkxConfig> {

    private static final int MAX_PARALLEL = 48;
    private final ExecutorService pool = Executors.newFixedThreadPool(MAX_PARALLEL);

    private volatile Map<String, TradingInstrument> symbolIndex;

    public OkxExchangeClient(OkxConfig config) {
        super(config);
    }

    @Override
    public ExchangeType getExchangeType() {
        return ExchangeType.OKX;
    }

    @Override
    protected List<TradingInstrument> fetchAvailableInstruments() {
        String url = config.getBaseUrl() + "/api/v5/public/instruments?instType=SWAP";
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(config.getTimeout()))
                .GET()
                .build();

        OkxResponse<OkxInstrumentItem> resp = sendRequest(req, new TypeReference<>() {
        });

        if (resp == null || !"0".equals(resp.code()) || resp.data() == null) {
            throw new ExchangeException("OKX instruments error: " + (resp != null ? resp.msg() : "null"));
        }

        List<TradingInstrument> list = resp.data().stream()
                .filter(i -> "SWAP".equalsIgnoreCase(i.instType()) && "live".equalsIgnoreCase(i.state()))
                .map(this::toTradingInstrument)
                .toList();

        symbolIndex = list.stream()
                .filter(i -> i.nativeSymbol() != null && !i.nativeSymbol().isBlank())
                .collect(Collectors.toUnmodifiableMap(TradingInstrument::nativeSymbol, Function.identity()));

        return list;
    }

    private TradingInstrument toTradingInstrument(OkxInstrumentItem it) {
        String[] parts = it.instId().split("-");
        String base = parts.length > 0 ? parts[0] : "";
        String quote = parts.length > 1 ? parts[1] : "";
        return new TradingInstrument(base, quote, InstrumentType.PERPETUAL, it.instId());
    }

    private String ensureSymbol(TradingInstrument inst) {
        return inst.nativeSymbol() != null ? inst.nativeSymbol() : inst.baseAsset() + "-" + inst.quoteAsset() + "-SWAP";
    }

    @Override
    public TickerData getTicker(TradingInstrument instrument) {
        String symbol = ensureSymbol(instrument);
        String url = config.getBaseUrl() + "/api/v5/market/ticker?instId=" + symbol;

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(config.getTimeout()))
                .GET()
                .build();

        OkxResponse<OkxTickerItem> resp = sendRequest(req, new TypeReference<>() {
        });

        if (resp == null || !"0".equals(resp.code()) || resp.data() == null || resp.data().isEmpty()) {
            throw new ExchangeException("OKX ticker error: " + (resp != null ? resp.msg() : "null"));
        }

        OkxTickerItem t = resp.data().getFirst();
        return new TickerData(
                instrument,
                bd(t.last()),
                bd(t.bidPx()),
                bd(t.askPx()),
                bd(t.high24h()),
                bd(t.low24h()),
                bd(t.vol24h()),
                System.currentTimeMillis()
        );
    }

    @Override
    public List<TickerData> getTickers(List<TradingInstrument> instruments) {
        String url = config.getBaseUrl() + "/api/v5/market/tickers?instType=SWAP";
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(config.getTimeout()))
                .GET()
                .build();

        OkxResponse<OkxTickerItem> resp = sendRequest(req, new TypeReference<>() {
        });
        if (resp == null || !"0".equals(resp.code()) || resp.data() == null) {
            throw new ExchangeException("OKX all-tickers error: "
                    + (resp != null ? resp.msg() : "null"));
        }

        Map<String, OkxTickerItem> bySymbol = resp.data().stream()
                .collect(Collectors.toMap(
                        OkxTickerItem::instId,
                        Function.identity(),
                        (a, b) -> a
                ));

        long now = System.currentTimeMillis();


        return instruments.stream()
                .map(inst -> {
                    OkxTickerItem t = bySymbol.get(ensureSymbol(inst));
                    if (t == null) return null;
                    return new TickerData(
                            inst,
                            bd(t.last()),
                            bd(t.bidPx()),
                            bd(t.askPx()),
                            bd(t.high24h()),
                            bd(t.low24h()),
                            bd(t.vol24h()),
                            now
                    );
                })
                .filter(Objects::nonNull)
                .toList();
    }

    @Override
    @Cacheable(cacheNames = "okx-funding", key = "#instrument.nativeSymbol()", cacheManager = "caffeineCacheManager")
    public FundingRateData getFundingRate(TradingInstrument instrument) {
        String symbol = ensureSymbol(instrument);
        String url = config.getBaseUrl() + "/api/v5/public/funding-rate?instId=" + symbol;

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(config.getTimeout()))
                .GET()
                .build();

        OkxResponse<OkxFundingItem> resp = sendRequest(req, new TypeReference<>() {
        });

        if (resp == null || !"0".equals(resp.code()) || resp.data() == null || resp.data().isEmpty()) {
            throw new ExchangeException("OKX funding error: " + (resp != null ? resp.msg() : "null"));
        }

        OkxFundingItem f = resp.data().get(0);
        BigDecimal rate = bd(f.fundingRate());
        long next = l(f.nextFundingTime());

        return new FundingRateData(instrument, rate, next);
    }

    @Override
    public List<FundingRateData> getAllFundingRates() {
        List<TradingInstrument> instruments = getAvailableInstruments().stream()
                .filter(i -> i.nativeSymbol().endsWith("-USDT-SWAP"))
                .toList();

        List<CompletableFuture<FundingRateData>> futures = instruments.stream()
                .map(inst -> CompletableFuture.supplyAsync(() -> {
                    try {
                        return getFundingRate(inst);
                    } catch (Exception e) {
                        return null;
                    }
                }, pool))
                .toList();

        return futures.stream()
                .map(CompletableFuture::join)
                .filter(Objects::nonNull)
                .toList();
    }
}
