package net.protsenko.fundy.app.exchange.impl.gateio;


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
public class GateioExchangeClient extends AbstractExchangeClient<GateioConfig> {

    public GateioExchangeClient(GateioConfig config) {
        super(config);
    }

    @Override
    public List<InstrumentData> getInstruments() {
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

        return list.stream()
                .filter(c -> "trading".equalsIgnoreCase(c.status()))
                .map(this::toInstrument)
                .toList();
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

        String url = config.getBaseUrl() + "/api/v4/futures/" + config.getSettle() + "/contracts";
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(config.getTimeout()))
                .GET()
                .build();

        List<GateioContractItem> list = sendRequest(req, new TypeReference<>() {});
        if (list == null) throw new ExchangeException("GateIO contracts: null response");

        GateioContractItem meta = list.stream()
                .filter(c -> symbol.equalsIgnoreCase(c.name()))
                .findFirst()
                .orElseThrow(() -> new ExchangeException("No contract meta for " + symbol));

        return new FundingRateData(
                getExchangeType(),
                instrument,
                bd(meta.fundingRate()),
                meta.fundingNextApply() * 1000L
        );
    }

    @Override
    public List<FundingRateData> getFundingRates(List<InstrumentData> instruments) {
        String url = config.getBaseUrl() + "/api/v4/futures/" + config.getSettle() + "/contracts";
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(config.getTimeout()))
                .GET()
                .build();

        List<GateioContractItem> list = sendRequest(req, new TypeReference<>() {});
        if (list == null) throw new ExchangeException("GateIO contracts: null response");

        Map<String, InstrumentData> requested = instruments.stream()
                .collect(Collectors.toMap(this::ensureSymbol, Function.identity(), (a, b) -> a));

        return list.stream()
                .filter(c -> "trading".equalsIgnoreCase(c.status()))
                .filter(c -> requested.containsKey(c.name()))
                .map(c -> new FundingRateData(
                        getExchangeType(),
                        requested.get(c.name()),
                        bd(c.fundingRate()),
                        c.fundingNextApply() * 1000L
                ))
                .toList();
    }

    @Override
    public ExchangeType getExchangeType() {
        return ExchangeType.GATEIO;
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

    @Override
    public Boolean isEnabled() {
        return config.isEnabled();
    }

    private String ensureSymbol(InstrumentData inst) {
        return inst.nativeSymbol() != null ? inst.nativeSymbol()
                : inst.baseAsset() + "_" + inst.quoteAsset();
    }
}
