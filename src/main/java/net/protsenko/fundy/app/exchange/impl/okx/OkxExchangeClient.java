package net.protsenko.fundy.app.exchange.impl.okx;

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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.stream.Collectors;

import static net.protsenko.fundy.app.utils.ExchangeUtils.bd;
import static net.protsenko.fundy.app.utils.ExchangeUtils.l;

@Component
public class OkxExchangeClient extends AbstractExchangeClient<OkxConfig> {

    private static final int MAX_PARALLEL = 48;
    private final ExecutorService pool = Executors.newFixedThreadPool(MAX_PARALLEL);

    public OkxExchangeClient(OkxConfig config) {
        super(config);
    }

    @Override
    public List<InstrumentData> getInstruments() {
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

        return resp.data().stream()
                .filter(i -> "SWAP".equalsIgnoreCase(i.instType()) && "live".equalsIgnoreCase(i.state()))
                .map(this::toInstrumentData)
                .toList();
    }

    @Override
    public TickerData getTicker(InstrumentData instrument) {
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
    public List<TickerData> getTickers(List<InstrumentData> instruments) {
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
    public FundingRateData getFundingRate(InstrumentData instrument) {
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

        OkxFundingItem f = resp.data().getFirst();
        BigDecimal rate = bd(f.fundingRate());
        long next = l(f.nextFundingTime());

        return new FundingRateData(
                getExchangeType(),
                instrument,
                rate,
                next
        );
    }

    @Override
    public List<FundingRateData> getFundingRates(List<InstrumentData> instruments) {
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

    @Override
    public ExchangeType getExchangeType() {
        return ExchangeType.OKX;
    }

    @Override
    public Boolean isEnabled() {
        return config.isEnabled();
    }

    private InstrumentData toInstrumentData(OkxInstrumentItem it) {
        String[] parts = it.instId().split("-");
        String base = parts.length > 0 ? parts[0] : "";
        String quote = parts.length > 1 ? parts[1] : "";
        return new InstrumentData(
                base,
                quote,
                InstrumentType.PERPETUAL,
                it.instId(),
                getExchangeType()
        );
    }

    private String ensureSymbol(InstrumentData inst) {
        return inst.nativeSymbol() != null ? inst.nativeSymbol() : inst.baseAsset() + "-" + inst.quoteAsset() + "-SWAP";
    }
}
