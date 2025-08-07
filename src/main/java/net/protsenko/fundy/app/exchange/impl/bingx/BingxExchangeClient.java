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

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static net.protsenko.fundy.app.utils.ExchangeUtils.bd;
import static net.protsenko.fundy.app.utils.ExchangeUtils.blank;

@Component
public class BingxExchangeClient extends AbstractExchangeClient<BingxConfig> {

    private volatile Map<String, InstrumentData> symbolIndex;

    private volatile Map<String, BingxTickerItem> tickerCache;
    private volatile long tickerCacheTs;

    private volatile Map<String, BingxPremiumIndexItem> premiumCache;
    private volatile long premiumCacheTs;

    public BingxExchangeClient(BingxConfig config) {
        super(config);
    }

    private Map<String, BingxTickerItem> fetchTickers() {
        long now = System.currentTimeMillis();
        Map<String, BingxTickerItem> local = tickerCache;

        if (local == null || now - tickerCacheTs > 100) {
            HttpRequest req = unsignedGet("/openApi/swap/v2/quote/ticker", Map.of());
            BingxResponse<List<BingxTickerItem>> r =
                    sendRequest(req, new TypeReference<>() {
                    });

            if (r == null || r.code() != 0 || r.data() == null)
                throw new ExchangeException("BingX ticker error: " +
                        (r != null ? r.msg() : "null"));

            local = r.data().stream()
                    .collect(Collectors.toMap(BingxTickerItem::symbol,
                            Function.identity(),
                            (a, b) -> a));
            tickerCache = local;
            tickerCacheTs = now;
        }
        return local;
    }

    private Map<String, BingxPremiumIndexItem> fetchPremiumIndex() {
        long now = System.currentTimeMillis();
        var local = premiumCache;

        if (local == null || now - premiumCacheTs > 500) {
            HttpRequest req = signedGet("/openApi/swap/v2/quote/premiumIndex", Map.of());
            BingxResponse<List<BingxPremiumIndexItem>> r =
                    sendRequest(req, new TypeReference<>() {
                    });

            if (r == null || r.code() != 0 || r.data() == null)
                throw new ExchangeException("BingX premiumIndex error: " +
                        (r != null ? r.msg() : "null"));

            local = r.data().stream()
                    .collect(Collectors.toMap(BingxPremiumIndexItem::symbol,
                            Function.identity(),
                            (a, b) -> a));
            premiumCache = local;
            premiumCacheTs = now;
        }
        return local;
    }

    @Override
    public ExchangeType getExchangeType() {
        return ExchangeType.BINGX;
    }

    @Override
    protected List<InstrumentData> fetchAvailableInstruments() {
        String path = "/openApi/swap/v2/quote/contracts";
        HttpRequest req = signedGet(path, Collections.emptyMap());

        BingxResponse<List<BingxContractItem>> resp =
                sendRequest(req, new TypeReference<>() {
                });

        if (resp == null || resp.code() != 0 || resp.data() == null) {
            throw new ExchangeException("BingX contracts error: " + (resp != null ? resp.msg() : "null"));
        }

        List<InstrumentData> list = resp.data().stream()
                .filter(i -> i.status() == 1)
                .map(i -> new InstrumentData(
                        i.asset(),
                        i.currency(),
                        InstrumentType.PERPETUAL,
                        i.symbol(),
                        getExchangeType()
                ))
                .toList();

        symbolIndex = list.stream()
                .filter(i -> i.nativeSymbol() != null && !i.nativeSymbol().isBlank())
                .collect(Collectors.toUnmodifiableMap(InstrumentData::nativeSymbol, Function.identity()));

        return list;
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

    private String ensureSymbol(InstrumentData inst) {
        return inst.nativeSymbol() != null ? inst.nativeSymbol()
                : inst.baseAsset() + "-" + inst.quoteAsset();
    }

    @Override
    public FundingRateData getFundingRate(InstrumentData inst) {
        var pi = fetchPremiumIndex().get(ensureSymbol(inst));
        if (pi == null)
            throw new ExchangeException("No premiumIndex for " + inst.nativeSymbol());

        return toFunding(inst, pi);
    }

    @Override
    public List<FundingRateData> getAllFundingRates() {
        var dict = symbolIndex();
        return fetchPremiumIndex().values().stream()
                .map(pi -> {
                    var inst = dict.get(pi.symbol());
                    return inst == null ? null : toFunding(inst, pi);
                })
                .filter(Objects::nonNull)
                .toList();
    }

    private FundingRateData toFunding(InstrumentData i, BingxPremiumIndexItem pi) {
        return new FundingRateData(
                getExchangeType(),
                i,
                bd(pi.lastFundingRate()),
                pi.nextFundingTime()
        );
    }

    @Override
    public TickerData getTicker(InstrumentData inst) {
        BingxTickerItem t = fetchTickers().get(ensureSymbol(inst));
        if (t == null)
            throw new ExchangeException("No ticker for " + inst.nativeSymbol());

        return toTicker(inst, t, System.currentTimeMillis());
    }

    @Override
    public List<TickerData> getTickers(List<InstrumentData> instruments) {
        var map = fetchTickers();
        long ts = System.currentTimeMillis();

        return instruments.stream()
                .map(i -> {
                    var t = map.get(ensureSymbol(i));
                    return t == null ? null : toTicker(i, t, ts);
                })
                .filter(Objects::nonNull)
                .toList();
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

    private HttpRequest signedGet(String path, Map<String, String> params) {
        if (blank(config.getApiKey()) || blank(config.getSecretKey())) {
            return unsignedGet(path, params);
        }

        Map<String, String> p = new TreeMap<>();
        if (params != null) {
            params.forEach((k, v) -> {
                if (blank(k) && v != null) p.put(k, v);
            });
        }
        p.put("timestamp", String.valueOf(System.currentTimeMillis()));

        String qs = buildQuery(p);
        String sign = hmacSha256Hex(qs, config.getSecretKey());

        String url = config.getBaseUrl() + path + "?" + qs + "&signature=" + sign;

        return HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(config.getTimeout()))
                .header("X-BX-APIKEY", config.getApiKey())
                .GET()
                .build();
    }

    private HttpRequest unsignedGet(String path, Map<String, String> params) {
        String qs = buildQuery(params == null ? Map.of() : params);
        String url = config.getBaseUrl() + path + (qs.isEmpty() ? "" : "?" + qs);
        return HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(config.getTimeout()))
                .GET()
                .build();
    }

    private String buildQuery(Map<String, String> params) {
        return params.entrySet().stream()
                .filter(e -> !blank(e.getKey()) && e.getValue() != null)
                .sorted(Map.Entry.comparingByKey())
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining("&"));
    }

    private String hmacSha256Hex(String data, String secret) {
        if (blank(secret)) throw new IllegalArgumentException("Empty key");
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
