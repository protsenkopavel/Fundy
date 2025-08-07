package net.protsenko.fundy.app.exchange.impl.gateio;


import com.fasterxml.jackson.core.type.TypeReference;
import net.protsenko.fundy.app.dto.*;
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
public class GateioExchangeClient extends AbstractExchangeClient<GateioConfig> {

    private volatile Map<String, InstrumentData> symbolIndex;
    private volatile Map<String, GateioContractItem> contractMeta;

    public GateioExchangeClient(GateioConfig config) {
        super(config);
    }

    @Override
    public ExchangeType getExchangeType() {
        return ExchangeType.GATEIO;
    }

    @Override
    protected List<InstrumentData> fetchAvailableInstruments() {
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

        List<InstrumentData> result = list.stream()
                .filter(c -> "trading".equalsIgnoreCase(c.status()))
                .map(this::toInstrument)
                .toList();

        symbolIndex = result.stream()
                .filter(i -> i.nativeSymbol() != null && !i.nativeSymbol().isBlank())
                .collect(Collectors.toUnmodifiableMap(InstrumentData::nativeSymbol, Function.identity()));

        contractMeta = list.stream()
                .collect(Collectors.toUnmodifiableMap(GateioContractItem::name, Function.identity()));

        return result;
    }

    private InstrumentData toInstrument(GateioContractItem c) {
        String nativeSymbol = c.name();
        String[] parts = nativeSymbol.split("_");
        String base = parts.length > 0 ? parts[0] : "";
        String quote = parts.length > 1 ? parts[1] : config.getSettle().toUpperCase();
        return new InstrumentData(
                base,
                quote,
                InstrumentType.PERPETUAL,
                nativeSymbol,
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

    private Map<String, GateioContractItem> contractMeta() {
        Map<String, GateioContractItem> local = contractMeta;
        if (local == null) {
            fetchAvailableInstruments();
            local = contractMeta;
        }
        return local;
    }

    private String ensureSymbol(InstrumentData inst) {
        return inst.nativeSymbol() != null ? inst.nativeSymbol()
                : inst.baseAsset() + "_" + inst.quoteAsset();
    }

    @Override
    public TickerData getTicker(InstrumentData instrument) {
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
                bd(t.last()),
                bd(t.highestBid()),
                bd(t.lowestAsk()),
                bd(t.high24h()),
                bd(t.low24h()),
                bd(t.volume24h()),
                System.currentTimeMillis()
        );
    }

    @Override
    public List<TickerData> getTickers(List<InstrumentData> instruments) {
        String url = "https://api.gateio.ws/api/v4/futures/" + config.getSettle() + "/tickers";
        HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();

        List<GateioTickerItem> list = sendRequest(req, new TypeReference<>() {
        });
        if (list == null) throw new ExchangeException("GateIO tickers: null response");

        Map<String, GateioTickerItem> bySymbol = list.stream()
                .collect(Collectors.toMap(GateioTickerItem::contract, Function.identity()));

        long now = System.currentTimeMillis();

        return instruments.stream()
                .map(inst -> {
                    GateioTickerItem t = bySymbol.get(ensureSymbol(inst));
                    if (t == null) return null;
                    return new TickerData(
                            inst,
                            bd(t.last()),
                            bd(t.highestBid()),
                            bd(t.lowestAsk()),
                            bd(t.high24h()),
                            bd(t.low24h()),
                            bd(t.volume24h()),
                            now
                    );
                })
                .filter(Objects::nonNull)
                .toList();
    }

    @Override
    public FundingRateData getFundingRate(InstrumentData instrument) {
        String symbol = ensureSymbol(instrument);
        GateioContractItem meta = contractMeta().get(symbol);
        if (meta == null) throw new ExchangeException("No contract meta for " + symbol);

        BigDecimal rate = bd(meta.fundingRate());
        long nextMs = meta.fundingNextApply() * 1000L;

        return new FundingRateData(
                getExchangeType(),
                instrument,
                rate,
                nextMs
        );
    }

    @Override
    public List<FundingRateData> getAllFundingRates() {
        Map<String, GateioContractItem> meta = contractMeta();
        Map<String, InstrumentData> dict = symbolIndex();

        return meta.values().stream()
                .filter(c -> "trading".equalsIgnoreCase(c.status()))
                .map(c -> {
                    InstrumentData inst = dict.get(c.name());
                    if (inst == null) return null;
                    return new FundingRateData(
                            getExchangeType(),
                            inst,
                            bd(c.fundingRate()),
                            c.fundingNextApply() * 1000L
                    );
                })
                .filter(Objects::nonNull)
                .toList();
    }
}
