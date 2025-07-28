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
import java.util.concurrent.Executor;
import java.util.concurrent.Semaphore;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class OkxExchangeClient extends AbstractExchangeClient<OkxConfig> {

    private static final int MAX_PARALLEL = 36;
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

    private Map<String, TradingInstrument> symbolIndex() {
        Map<String, TradingInstrument> local = symbolIndex;
        if (local == null) {
            local = getAvailableInstruments().stream()
                    .collect(Collectors.toUnmodifiableMap(TradingInstrument::nativeSymbol, Function.identity()));
            symbolIndex = local;
        }
        return local;
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
                d(t.last()),
                d(t.bidPx()),
                d(t.askPx()),
                d(t.high24h()),
                d(t.low24h()),
                d(t.vol24h()),
                System.currentTimeMillis()
        );
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

        Semaphore gate = new Semaphore(MAX_PARALLEL);
        Executor exec = Runnable::run;

        List<CompletableFuture<FundingRateData>> futures = instruments.stream()
                .map(inst -> CompletableFuture.supplyAsync(() -> {
                    try {
                        gate.acquire();
                        return getFundingRate(inst);
                    } catch (Exception e) {
                        return null;
                    } finally {
                        gate.release();
                    }
                }, exec))
                .toList();

        return futures.stream()
                .map(f -> {
                    try {
                        return f.get();
                    } catch (Exception e) {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .toList();
    }

    private double d(String s) {
        if (s == null || s.isBlank()) return 0.0;
        try {
            return Double.parseDouble(s);
        } catch (Exception e) {
            return 0.0;
        }
    }

    private BigDecimal bd(String s) {
        if (s == null || s.isBlank()) return BigDecimal.ZERO;
        try {
            return new BigDecimal(s);
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    private long l(String s) {
        if (s == null || s.isBlank()) return 0L;
        try {
            return Long.parseLong(s);
        } catch (Exception e) {
            return 0L;
        }
    }
}
