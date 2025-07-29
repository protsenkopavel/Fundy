package net.protsenko.fundy.app.exchange.impl.bingx;

import com.fasterxml.jackson.core.type.TypeReference;
import net.protsenko.fundy.app.dto.FundingRateData;
import net.protsenko.fundy.app.dto.InstrumentType;
import net.protsenko.fundy.app.dto.TickerData;
import net.protsenko.fundy.app.dto.TradingInstrument;
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

import static net.protsenko.fundy.app.utils.ExchangeUtils.*;

@Component
public class BingxExchangeClient extends AbstractExchangeClient<BingxConfig> {

    private volatile Map<String, TradingInstrument> symbolIndex;

    public BingxExchangeClient(BingxConfig config) {
        super(config);
    }

    @Override
    public ExchangeType getExchangeType() {
        return ExchangeType.BINGX;
    }

    @Override
    protected List<TradingInstrument> fetchAvailableInstruments() {
        String path = "/openApi/swap/v2/quote/contracts";
        HttpRequest req = signedGet(path, Collections.emptyMap());

        BingxResponse<List<BingxContractItem>> resp =
                sendRequest(req, new TypeReference<>() {
                });

        if (resp == null || resp.code() != 0 || resp.data() == null) {
            throw new ExchangeException("BingX contracts error: " + (resp != null ? resp.msg() : "null"));
        }

        List<TradingInstrument> list = resp.data().stream()
                .filter(i -> i.status() == 1)
                .map(i -> new TradingInstrument(
                        i.asset(),
                        i.currency(),
                        InstrumentType.PERPETUAL,
                        i.symbol()
                ))
                .toList();

        symbolIndex = list.stream()
                .filter(i -> i.nativeSymbol() != null && !i.nativeSymbol().isBlank())
                .collect(Collectors.toUnmodifiableMap(TradingInstrument::nativeSymbol, Function.identity()));

        return list;
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
        return inst.nativeSymbol() != null ? inst.nativeSymbol()
                : inst.baseAsset() + "-" + inst.quoteAsset();
    }

    @Override
    public FundingRateData getFundingRate(TradingInstrument instrument) {
        String symbol = ensureSymbol(instrument);
        BingxPremiumIndexItem pi = premiumIndexAll().stream()
                .filter(x -> x.symbol().equalsIgnoreCase(symbol))
                .findFirst()
                .orElseThrow(() -> new ExchangeException("No premiumIndex for " + symbol));

        return new FundingRateData(
                instrument,
                bd(pi.lastFundingRate()),
                pi.nextFundingTime()
        );
    }

    @Override
    public List<FundingRateData> getAllFundingRates() {
        List<BingxPremiumIndexItem> items = premiumIndexAll();
        Map<String, TradingInstrument> dict = symbolIndex();

        return items.stream()
                .map(pi -> {
                    TradingInstrument inst = dict.get(pi.symbol());
                    if (inst == null) return null;
                    return new FundingRateData(
                            inst,
                            bd(pi.lastFundingRate()),
                            pi.nextFundingTime()
                    );
                })
                .filter(Objects::nonNull)
                .toList();
    }

    private List<BingxPremiumIndexItem> premiumIndexAll() {
        String path = "/openApi/swap/v2/quote/premiumIndex";
        HttpRequest req = signedGet(path, Collections.emptyMap());

        BingxResponse<List<BingxPremiumIndexItem>> resp =
                sendRequest(req, new TypeReference<>() {
                });

        if (resp == null || resp.code() != 0 || resp.data() == null) {
            throw new ExchangeException("BingX premiumIndex error: " + (resp != null ? resp.msg() : "null"));
        }
        return resp.data();
    }

    @Override
    public TickerData getTicker(TradingInstrument instrument) {
        BingxPremiumIndexItem pi = premiumIndexAll().stream()
                .filter(x -> x.symbol().equalsIgnoreCase(ensureSymbol(instrument)))
                .findFirst()
                .orElseThrow(() -> new ExchangeException("No premiumIndex for ticker"));

        return new TickerData(
                instrument,
                d(pi.markPrice()),
                Double.NaN,
                Double.NaN,
                Double.NaN,
                Double.NaN,
                0.0,
                System.currentTimeMillis()
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
