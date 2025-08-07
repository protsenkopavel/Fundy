package net.protsenko.fundy.app.exchange.impl.kucoin;

import net.protsenko.fundy.app.dto.FundingRateData;
import net.protsenko.fundy.app.dto.InstrumentType;
import net.protsenko.fundy.app.dto.TickerData;
import net.protsenko.fundy.app.dto.TradingInstrument;
import net.protsenko.fundy.app.exception.ExchangeException;
import net.protsenko.fundy.app.exchange.AbstractExchangeClient;
import net.protsenko.fundy.app.exchange.ExchangeType;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpRequest;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.stream.Collectors;

import static net.protsenko.fundy.app.utils.ExchangeUtils.bd;
import static net.protsenko.fundy.app.utils.ExchangeUtils.d;

@Component
public class KucoinExchangeClient extends AbstractExchangeClient<KucoinConfig> {

    private static final int KUCOIN_PARALLEL = 16;
    private static final int RETRIES = 2;
    private final ExecutorService kuPool =
            Executors.newFixedThreadPool(KUCOIN_PARALLEL);
    private volatile Map<String, TradingInstrument> symbolIndex;
    private volatile Map<String, KucoinContractItem> rawContractIndex;

    public KucoinExchangeClient(KucoinConfig config) {
        super(config);
    }

    @Override
    public ExchangeType getExchangeType() {
        return ExchangeType.KUCOIN;
    }

    @Override
    protected List<TradingInstrument> fetchAvailableInstruments() {
        String url = config.getBaseUrl() + "/api/v1/contracts/active";
        HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();

        KucoinContractsResponse resp = sendRequest(req, KucoinContractsResponse.class);
        if (resp == null || resp.data() == null || resp.data().isEmpty()) {
            throw new ExchangeException("KuCoin returned empty contracts list");
        }

        List<TradingInstrument> list = resp.data().stream()
                .filter(i -> "Open".equalsIgnoreCase(i.status()))
                .map(i -> new TradingInstrument(
                        i.baseCurrency(),
                        i.quoteCurrency(),
                        InstrumentType.PERPETUAL,
                        i.symbol()
                ))
                .toList();

        this.symbolIndex = list.stream()
                .collect(Collectors.toUnmodifiableMap(TradingInstrument::nativeSymbol, Function.identity()));

        this.rawContractIndex = resp.data().stream()
                .collect(Collectors.toUnmodifiableMap(KucoinContractItem::symbol, Function.identity()));

        return list;
    }

    @Override
    public TickerData getTicker(TradingInstrument instrument) {
        String symbol = instrument.nativeSymbol();
        if (symbol == null) {
            symbol = instrument.baseAsset() + instrument.quoteAsset() + "M";
        }

        String url = config.getBaseUrl() + "/api/v1/ticker?symbol=" + symbol;
        HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();

        KucoinTickerResponse resp = sendRequest(req, KucoinTickerResponse.class);
        if (resp == null || !"200000".equals(resp.code()) || resp.data() == null) {
            throw new ExchangeException("KuCoin ticker error: " + (resp != null ? resp.code() : "null"));
        }

        KucoinTickerData td = resp.data();
        KucoinContractItem c = contract(symbol);

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
    @Cacheable(cacheNames = "exchange-tickers",
            key = "'KUCOIN'",
            cacheManager = "caffeineCacheManager")
    public List<TickerData> getTickers(List<TradingInstrument> instruments) {
        return instruments.stream()
                .map(inst -> CompletableFuture.supplyAsync(() -> fetchWithRetry(inst), kuPool))
                .map(CompletableFuture::join)
                .filter(Objects::nonNull)
                .toList();
    }

    private TickerData fetchWithRetry(TradingInstrument inst) {
        for (int i = 0; i <= RETRIES; i++) {
            try {
                return getTicker(inst);
            } catch (Exception ex) {
                if (i == RETRIES || !ex.getMessage().contains("too many concurrent"))
                    return null;
                try {
                    Thread.sleep(200L);
                } catch (InterruptedException ignored) {
                }
            }
        }
        return null;
    }

    @Override
    public FundingRateData getFundingRate(TradingInstrument instrument) {
        KucoinContractItem c = contract(instrument.nativeSymbol());
        return new FundingRateData(
                instrument,
                bd(c.fundingFeeRate()),
                c.nextFundingRateDateTime()
        );
    }

    @Override
    public List<FundingRateData> getAllFundingRates() {
        Map<String, TradingInstrument> dict = symbolIndex();
        return rawContractIndex().values().stream()
                .filter(c -> "Open".equalsIgnoreCase(c.status()))
                .map(c -> new FundingRateData(
                        dict.get(c.symbol()),
                        bd(c.fundingFeeRate()),
                        c.nextFundingRateDateTime()
                ))
                .toList();
    }

    private KucoinContractItem contract(String symbol) {
        KucoinContractItem c = rawContractIndex().get(symbol);
        if (c == null) throw new ExchangeException("KuCoin contract not found: " + symbol);
        return c;
    }

    private Map<String, TradingInstrument> symbolIndex() {
        Map<String, TradingInstrument> local = symbolIndex;
        if (local == null) {
            fetchAvailableInstruments();
            local = symbolIndex;
        }
        return local;
    }

    private Map<String, KucoinContractItem> rawContractIndex() {
        Map<String, KucoinContractItem> local = rawContractIndex;
        if (local == null) {
            fetchAvailableInstruments();
            local = rawContractIndex;
        }
        return local;
    }
}
