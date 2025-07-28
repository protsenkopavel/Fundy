package net.protsenko.fundy.app.exchange.impl.htx;

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
public class HtxExchangeClient extends AbstractExchangeClient<HtxConfig> {

    private volatile Map<String, TradingInstrument> symbolIndex;

    public HtxExchangeClient(HtxConfig config) {
        super(config);
    }

    @Override
    public ExchangeType getExchangeType() {
        return ExchangeType.HTX;
    }

    @Override
    protected List<TradingInstrument> fetchAvailableInstruments() {
        String url = config.getBaseUrl() + "/linear-swap-api/v1/swap_contract_info";
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(config.getTimeout()))
                .GET()
                .build();

        HtxResp<List<HtxContractItem>> resp =
                sendRequest(req, new TypeReference<>() {
                });

        if (resp == null || !"ok".equalsIgnoreCase(resp.status()) || resp.data() == null) {
            throw new ExchangeException("HTX instruments error: " + (resp != null ? resp.status() : "null"));
        }

        List<TradingInstrument> list = resp.data().stream()
                .filter(c -> c.contractStatus() == 1)
                .map(this::toInstrument)
                .toList();

        symbolIndex = list.stream()
                .collect(Collectors.toUnmodifiableMap(TradingInstrument::nativeSymbol, Function.identity()));

        return list;
    }

    private TradingInstrument toInstrument(HtxContractItem c) {
        String[] parts = c.contractCode().split("-");
        String base = parts.length > 0 ? parts[0] : c.symbol();
        String quote = parts.length > 1 ? parts[1] : c.tradePartition();
        return new TradingInstrument(base, quote, InstrumentType.PERPETUAL, c.contractCode());
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
                : inst.baseAsset().toUpperCase() + "-" + inst.quoteAsset().toUpperCase();
    }

    @Override
    public FundingRateData getFundingRate(TradingInstrument instrument) {
        String code = ensureSymbol(instrument);
        String url = config.getBaseUrl() + "/linear-swap-api/v1/swap_funding_rate?contract_code=" + code;

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(config.getTimeout()))
                .GET()
                .build();

        HtxResp<HtxFundingSingle> resp =
                sendRequest(req, new TypeReference<>() {
                });

        if (resp == null || !"ok".equalsIgnoreCase(resp.status()) || resp.data() == null) {
            throw new ExchangeException("HTX funding error for " + code + ": " + (resp != null ? resp.status() : "null"));
        }

        HtxFundingSingle d = resp.data();
        BigDecimal rate = bd(d.fundingRate());
        long next = l(d.fundingTime());

        return new FundingRateData(instrument, rate, next);
    }

    @Override
    public List<FundingRateData> getAllFundingRates() {
        String url = config.getBaseUrl() + "/linear-swap-api/v1/swap_batch_funding_rate";
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(config.getTimeout()))
                .GET()
                .build();

        HtxResp<List<HtxFundingItem>> resp =
                sendRequest(req, new TypeReference<>() {
                });

        if (resp == null || !"ok".equalsIgnoreCase(resp.status()) || resp.data() == null) {
            throw new ExchangeException("HTX batch funding error: " + (resp != null ? resp.status() : "null"));
        }

        Map<String, TradingInstrument> dict = symbolIndex();

        return resp.data().stream()
                .map(fi -> {
                    TradingInstrument inst = dict.get(fi.contractCode());
                    if (inst == null) return null;
                    return new FundingRateData(
                            inst,
                            bd(fi.fundingRate()),
                            l(fi.fundingTime())
                    );
                })
                .filter(Objects::nonNull)
                .toList();
    }

    @Override
    public TickerData getTicker(TradingInstrument instrument) {
        String code = ensureSymbol(instrument);
        String url = config.getBaseUrl() + "/linear-swap-ex/market/detail?contract_code=" + code;

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(config.getTimeout()))
                .GET()
                .build();

        try {
            HtxDetailResp resp = sendRequest(req, HtxDetailResp.class);
            if (resp == null || !"ok".equalsIgnoreCase(resp.status()) || resp.tick() == null) {
                return emptyTicker(instrument);
            }
            HtxDetailResp.Tick t = resp.tick();
            return new TickerData(
                    instrument,
                    t.close(),
                    Double.NaN,
                    Double.NaN,
                    t.high(),
                    t.low(),
                    t.vol(),
                    System.currentTimeMillis()
            );
        } catch (ExchangeException ex) {
            return emptyTicker(instrument);
        }
    }

    private TickerData emptyTicker(TradingInstrument inst) {
        return new TickerData(inst, Double.NaN, Double.NaN, Double.NaN,
                Double.NaN, Double.NaN, 0.0, System.currentTimeMillis());
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
