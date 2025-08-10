package net.protsenko.fundy.app.exchange.impl.bingx;

import com.fasterxml.jackson.core.type.TypeReference;
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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import static net.protsenko.fundy.app.utils.ExchangeUtils.bd;

@Component
public class BingxExchangeClient extends AbstractExchangeClient<BingxConfig> {

    public BingxExchangeClient(BingxConfig config) {
        super(config);
    }

    @Override
    public List<InstrumentData> getInstruments() {
        String url = config.getBaseUrl() + "/openApi/swap/v2/quote/contracts";
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();

        BingxResponse<List<BingxContractItem>> resp = sendRequest(req, new TypeReference<>() {
        });

        if (resp == null || resp.code() != 0 || resp.data() == null) {
            throw new ExchangeException("BingX contracts error: " + (resp != null ? resp.msg() : "null"));
        }

        return resp.data().stream()
                .filter(i -> i.status() == 1)
                .map(i -> new InstrumentData(
                        i.asset(),
                        i.currency(),
                        InstrumentType.PERPETUAL,
                        i.symbol(),
                        getExchangeType()
                ))
                .toList();
    }

    @Override
    public TickerData getTicker(InstrumentData inst) {
        String url = config.getBaseUrl() + "/openApi/swap/v2/quote/ticker";
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();

        BingxResponse<List<BingxTickerItem>> r = sendRequest(req, new TypeReference<>() {
        });

        if (r == null || r.code() != 0 || r.data() == null) {
            throw new ExchangeException("BingX ticker error: " + (r != null ? r.msg() : "null"));
        }

        String symbol = ensureSymbol(inst);
        Map<String, BingxTickerItem> bySymbol = r.data().stream()
                .collect(Collectors.toMap(BingxTickerItem::symbol, Function.identity(), (a, b) -> a));

        BingxTickerItem t = bySymbol.get(symbol);
        if (t == null) throw new ExchangeException("No ticker for " + symbol);

        long ts = System.currentTimeMillis();
        return toTicker(inst, t, ts);
    }

    @Override
    public List<TickerData> getTickers(List<InstrumentData> instruments) {
        String url = config.getBaseUrl() + "/openApi/swap/v2/quote/ticker";
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();

        BingxResponse<List<BingxTickerItem>> r = sendRequest(req, new TypeReference<>() {
        });

        if (r == null || r.code() != 0 || r.data() == null) {
            throw new ExchangeException("BingX tickers error: " + (r != null ? r.msg() : "null"));
        }

        Map<String, BingxTickerItem> bySymbol = r.data().stream()
                .collect(Collectors.toMap(BingxTickerItem::symbol, Function.identity(), (a, b) -> a));

        long ts = System.currentTimeMillis();
        return instruments.stream()
                .map(i -> {
                    BingxTickerItem t = bySymbol.get(ensureSymbol(i));
                    return t == null ? null : toTicker(i, t, ts);
                })
                .filter(Objects::nonNull)
                .toList();
    }

    @Override
    public FundingRateData getFundingRate(InstrumentData inst) {
        String url = config.getBaseUrl() + "/openApi/swap/v2/quote/premiumIndex";
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();

        BingxResponse<List<BingxPremiumIndexItem>> r = sendRequest(req, new TypeReference<>() {
        });

        if (r == null || r.code() != 0 || r.data() == null) {
            throw new ExchangeException("BingX premiumIndex error: " + (r != null ? r.msg() : "null"));
        }

        String symbol = ensureSymbol(inst);
        Map<String, BingxPremiumIndexItem> bySymbol = r.data().stream()
                .collect(Collectors.toMap(BingxPremiumIndexItem::symbol, Function.identity(), (a, b) -> a));

        BingxPremiumIndexItem pi = bySymbol.get(symbol);
        if (pi == null) throw new ExchangeException("No premiumIndex for " + symbol);

        return toFunding(inst, pi);
    }

    @Override
    public List<FundingRateData> getFundingRates(List<InstrumentData> instruments) {
        String url = config.getBaseUrl() + "/openApi/swap/v2/quote/premiumIndex";
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();

        BingxResponse<List<BingxPremiumIndexItem>> r = sendRequest(req, new TypeReference<>() {
        });

        if (r == null || r.code() != 0 || r.data() == null) {
            throw new ExchangeException("BingX premiumIndex error: " + (r != null ? r.msg() : "null"));
        }

        Map<String, InstrumentData> requested = instruments.stream()
                .collect(Collectors.toMap(this::ensureSymbol, Function.identity(), (a, b) -> a));

        return r.data().stream()
                .filter(pi -> requested.containsKey(pi.symbol()))
                .map(pi -> toFunding(requested.get(pi.symbol()), pi))
                .toList();
    }

    @Override
    public ExchangeType getExchangeType() {
        return ExchangeType.BINGX;
    }

    @Override
    public Boolean isEnabled() {
        return config.isEnabled();
    }

    private String ensureSymbol(InstrumentData inst) {
        return inst.nativeSymbol() != null ? inst.nativeSymbol()
                : inst.baseAsset() + "-" + inst.quoteAsset();
    }

    private TickerData toTicker(InstrumentData i, BingxTickerItem t, long ts) {
        return new TickerData(
                i,
                bd(t.lastPrice()),
                bd(t.bestBid()),
                bd(t.bestAsk()),
                bd(t.high24h()),
                bd(t.low24h()),
                bd(t.volume24h()),
                ts
        );
    }

    private FundingRateData toFunding(InstrumentData i, BingxPremiumIndexItem pi) {
        return new FundingRateData(
                getExchangeType(),
                i,
                bd(pi.lastFundingRate()),
                pi.nextFundingTime()
        );
    }
}
