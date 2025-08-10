package net.protsenko.fundy.app.exchange.impl.kucoin;

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
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static net.protsenko.fundy.app.utils.ExchangeUtils.bd;

@Component
public class KucoinExchangeClient extends AbstractExchangeClient<KucoinConfig> {

    private static final int KUCOIN_PARALLEL = 16;
    private static final int RETRIES = 2;
    private final ExecutorService kuPool = Executors.newFixedThreadPool(KUCOIN_PARALLEL);

    public KucoinExchangeClient(KucoinConfig config) {
        super(config);
    }

    @Override
    public List<InstrumentData> getInstruments() {
        String url = config.getBaseUrl() + "/api/v1/contracts/active";
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(config.getTimeout()))
                .GET()
                .build();

        KucoinContractsResponse resp = sendRequest(req, KucoinContractsResponse.class);
        if (resp == null || resp.data() == null || resp.data().isEmpty()) {
            throw new ExchangeException("KuCoin returned empty contracts list");
        }

        return resp.data().stream()
                .filter(i -> "Open".equalsIgnoreCase(i.status()))
                .map(i -> new InstrumentData(
                        i.baseCurrency(),
                        i.quoteCurrency(),
                        InstrumentType.PERPETUAL,
                        i.symbol(),
                        getExchangeType()
                ))
                .toList();
    }

    @Override
    public TickerData getTicker(InstrumentData instrument) {
        String symbol = ensureSymbol(instrument);

        String urlTicker = config.getBaseUrl() + "/api/v1/ticker?symbol=" + symbol;
        HttpRequest reqTicker = HttpRequest.newBuilder()
                .uri(URI.create(urlTicker))
                .timeout(Duration.ofSeconds(config.getTimeout()))
                .GET()
                .build();

        KucoinTickerResponse tr = sendRequest(reqTicker, KucoinTickerResponse.class);
        if (tr == null || !"200000".equals(tr.code()) || tr.data() == null) {
            throw new ExchangeException("KuCoin ticker error: " + (tr != null ? tr.code() : "null"));
        }

        String urlContracts = config.getBaseUrl() + "/api/v1/contracts/active";
        HttpRequest reqContracts = HttpRequest.newBuilder()
                .uri(URI.create(urlContracts))
                .timeout(Duration.ofSeconds(config.getTimeout()))
                .GET()
                .build();

        KucoinContractsResponse cr = sendRequest(reqContracts, KucoinContractsResponse.class);
        if (cr == null || cr.data() == null) {
            throw new ExchangeException("KuCoin contracts fetch error: null");
        }

        KucoinContractItem c = cr.data().stream()
                .filter(it -> symbol.equalsIgnoreCase(it.symbol()))
                .findFirst()
                .orElseThrow(() -> new ExchangeException("KuCoin contract not found: " + symbol));

        KucoinTickerData td = tr.data();

        return new TickerData(
                instrument,
                bd(td.price()),
                bd(td.bestBidPrice()),
                bd(td.bestAskPrice()),
                bd(c.highPrice()),
                bd(c.lowPrice()),
                bd(c.volumeOf24h()),
                System.currentTimeMillis()
        );
    }

    @Override
    public List<TickerData> getTickers(List<InstrumentData> instruments) {
        return instruments.stream()
                .map(inst -> CompletableFuture.supplyAsync(() -> fetchWithRetry(inst), kuPool))
                .map(CompletableFuture::join)
                .filter(Objects::nonNull)
                .toList();
    }

    @Override
    public FundingRateData getFundingRate(InstrumentData instrument) {
        String symbol = ensureSymbol(instrument);

        String url = config.getBaseUrl() + "/api/v1/contracts/active";
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(config.getTimeout()))
                .GET()
                .build();

        KucoinContractsResponse resp = sendRequest(req, KucoinContractsResponse.class);
        if (resp == null || resp.data() == null) {
            throw new ExchangeException("KuCoin contracts fetch error: null");
        }

        KucoinContractItem c = resp.data().stream()
                .filter(it -> symbol.equalsIgnoreCase(it.symbol()))
                .findFirst()
                .orElseThrow(() -> new ExchangeException("KuCoin contract not found: " + symbol));

        return new FundingRateData(
                getExchangeType(),
                instrument,
                bd(c.fundingFeeRate()),
                c.nextFundingRateDateTime()
        );
    }

    @Override
    public List<FundingRateData> getFundingRates(List<InstrumentData> instruments) {
        String url = config.getBaseUrl() + "/api/v1/contracts/active";
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(config.getTimeout()))
                .GET()
                .build();

        KucoinContractsResponse resp = sendRequest(req, KucoinContractsResponse.class);
        if (resp == null || resp.data() == null) {
            throw new ExchangeException("Contracts fetch error: null response");
        }

        var requested = instruments.stream()
                .collect(java.util.stream.Collectors.toMap(this::ensureSymbol, java.util.function.Function.identity(), (a, b) -> a));

        return resp.data().stream()
                .filter(c -> "Open".equalsIgnoreCase(c.status()))
                .filter(c -> requested.containsKey(c.symbol()))
                .map(c -> new FundingRateData(
                        getExchangeType(),
                        requested.get(c.symbol()),
                        bd(c.fundingFeeRate()),
                        c.nextFundingRateDateTime()
                ))
                .toList();
    }

    @Override
    public ExchangeType getExchangeType() {
        return ExchangeType.KUCOIN;
    }

    @Override
    public Boolean isEnabled() {
        return config.isEnabled();
    }

    private TickerData fetchWithRetry(InstrumentData inst) {
        for (int i = 0; i <= RETRIES; i++) {
            try {
                return getTicker(inst);
            } catch (Exception ex) {
                if (i == RETRIES || (ex.getMessage() != null && !ex.getMessage().contains("too many concurrent"))) {
                    return null;
                }
                try {
                    Thread.sleep(200L);
                } catch (InterruptedException ignored) {
                }
            }
        }
        return null;
    }

    private String ensureSymbol(InstrumentData instrument) {
        if (instrument.nativeSymbol() != null) return instrument.nativeSymbol();
        return instrument.baseAsset() + instrument.quoteAsset() + "M";
    }
}
