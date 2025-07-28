package net.protsenko.fundy.app.exchange.impl.gateio;


import com.fasterxml.jackson.core.type.TypeReference;
import net.protsenko.fundy.app.dto.FundingRateData;
import net.protsenko.fundy.app.dto.InstrumentType;
import net.protsenko.fundy.app.dto.TickerData;
import net.protsenko.fundy.app.dto.TradingInstrument;
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

@Component
public class GateioExchangeClient extends AbstractExchangeClient<GateioConfig> {

    private volatile Map<String, TradingInstrument> symbolIndex;
    private volatile Map<String, GateioContractItem> contractMeta;

    public GateioExchangeClient(GateioConfig config) {
        super(config);
    }

    @Override
    public ExchangeType getExchangeType() {
        return ExchangeType.GATEIO;
    }

    @Override
    protected List<TradingInstrument> fetchAvailableInstruments() {
        String url = config.getBaseUrl() + "/api/v4/futures/" + config.getSettle() + "/contracts";
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(config.getTimeout()))
                .GET()
                .build();

        List<GateioContractItem> list = sendRequest(req, new TypeReference<>() {
        });

        if (list == null) {
            throw new ExchangeException("GateIO contracts: null response");
        }

        List<TradingInstrument> result = list.stream()
                .filter(c -> "trading".equalsIgnoreCase(c.status()))
                .map(this::toInstrument)
                .toList();

        symbolIndex = result.stream()
                .filter(i -> i.nativeSymbol() != null && !i.nativeSymbol().isBlank())
                .collect(Collectors.toUnmodifiableMap(TradingInstrument::nativeSymbol, Function.identity()));

        contractMeta = list.stream()
                .collect(Collectors.toUnmodifiableMap(GateioContractItem::name, Function.identity()));

        return result;
    }

    private TradingInstrument toInstrument(GateioContractItem c) {
        String nativeSymbol = c.name();
        String[] parts = nativeSymbol.split("_");
        String base = parts.length > 0 ? parts[0] : "";
        String quote = parts.length > 1 ? parts[1] : config.getSettle().toUpperCase();
        return new TradingInstrument(base, quote, InstrumentType.PERPETUAL, nativeSymbol);
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

    private Map<String, GateioContractItem> contractMeta() {
        Map<String, GateioContractItem> local = contractMeta;
        if (local == null) {
            fetchAvailableInstruments();
            local = contractMeta;
        }
        return local;
    }

    private String ensureSymbol(TradingInstrument inst) {
        return inst.nativeSymbol() != null ? inst.nativeSymbol()
                : inst.baseAsset() + "_" + inst.quoteAsset();
    }

    @Override
    public TickerData getTicker(TradingInstrument instrument) {
        String symbol = ensureSymbol(instrument);
        String url = config.getBaseUrl() + "/api/v4/futures/" + config.getSettle() + "/tickers";
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(config.getTimeout()))
                .GET()
                .build();

        List<GateioTickerItem> list = sendRequest(req, new TypeReference<>() {
        });
        if (list == null) throw new ExchangeException("GateIO tickers: null response");

        GateioTickerItem t = list.stream()
                .filter(x -> symbol.equalsIgnoreCase(x.contract()))
                .findFirst()
                .orElseThrow(() -> new ExchangeException("Ticker not found: " + symbol));

        return new TickerData(
                instrument,
                d(t.last()),
                d(t.highestBid()),
                d(t.lowestAsk()),
                d(t.high24h()),
                d(t.low24h()),
                d(t.volume24h()),
                System.currentTimeMillis()
        );
    }

    @Override
    public FundingRateData getFundingRate(TradingInstrument instrument) {
        String symbol = ensureSymbol(instrument);
        GateioContractItem meta = contractMeta().get(symbol);
        if (meta == null) throw new ExchangeException("No contract meta for " + symbol);

        BigDecimal rate = bd(meta.fundingRate());
        long nextMs = meta.fundingNextApply() * 1000L;

        return new FundingRateData(instrument, rate, nextMs);
    }

    @Override
    public List<FundingRateData> getAllFundingRates() {
        Map<String, GateioContractItem> meta = contractMeta();
        Map<String, TradingInstrument> dict = symbolIndex();

        return meta.values().stream()
                .filter(c -> "trading".equalsIgnoreCase(c.status()))
                .map(c -> {
                    TradingInstrument inst = dict.get(c.name());
                    if (inst == null) return null;
                    return new FundingRateData(
                            inst,
                            bd(c.fundingRate()),
                            c.fundingNextApply() * 1000L
                    );
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
}
