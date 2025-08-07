package net.protsenko.fundy.app.exchange.impl.htx;

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
import static net.protsenko.fundy.app.utils.ExchangeUtils.l;

@Component
public class HtxExchangeClient extends AbstractExchangeClient<HtxConfig> {

    private volatile Map<String, InstrumentData> symbolIndex;

    public HtxExchangeClient(HtxConfig config) {
        super(config);
    }

    @Override
    public ExchangeType getExchangeType() {
        return ExchangeType.HTX;
    }

    @Override
    protected List<InstrumentData> fetchAvailableInstruments() {
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

        List<InstrumentData> list = resp.data().stream()
                .filter(c -> c.contractStatus() == 1)
                .map(this::toInstrument)
                .toList();

        symbolIndex = list.stream()
                .collect(Collectors.toUnmodifiableMap(InstrumentData::nativeSymbol, Function.identity()));

        return list;
    }

    private InstrumentData toInstrument(HtxContractItem c) {
        String[] parts = c.contractCode().split("-");
        String base = parts.length > 0 ? parts[0] : c.symbol();
        String quote = parts.length > 1 ? parts[1] : c.tradePartition();
        return new InstrumentData(
                base,
                quote,
                InstrumentType.PERPETUAL,
                c.contractCode(),
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

    private String ensureSymbol(InstrumentData inst) {
        return inst.nativeSymbol() != null ? inst.nativeSymbol()
                : inst.baseAsset().toUpperCase() + "-" + inst.quoteAsset().toUpperCase();
    }

    @Override
    public FundingRateData getFundingRate(InstrumentData instrument) {
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

        return new FundingRateData(
                getExchangeType(),
                instrument,
                rate,
                next
        );
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

        Map<String, InstrumentData> dict = symbolIndex();

        return resp.data().stream()
                .map(fi -> {
                    InstrumentData inst = dict.get(fi.contractCode());
                    if (inst == null) return null;
                    return new FundingRateData(
                            getExchangeType(),
                            inst,
                            bd(fi.fundingRate()),
                            l(fi.fundingTime())
                    );
                })
                .filter(Objects::nonNull)
                .toList();
    }

    @Override
    public TickerData getTicker(InstrumentData instrument) {
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
                    bd(String.valueOf(t.close())),
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    bd(String.valueOf(t.high())),
                    bd(String.valueOf(t.low())),
                    bd(String.valueOf(t.vol())),
                    System.currentTimeMillis()
            );
        } catch (ExchangeException ex) {
            return emptyTicker(instrument);
        }
    }

    @Override
    public List<TickerData> getTickers(List<InstrumentData> instruments) {
        String url = config.getBaseUrl() + "/linear-swap-ex/market/detail/batch_merged";
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(config.getTimeout()))
                .GET()
                .build();

        HtxBatchResp resp = sendRequest(req, HtxBatchResp.class);
        if (resp == null || !"ok".equalsIgnoreCase(resp.status()) || resp.ticks() == null) {
            throw new ExchangeException("HTX batch tickers error: "
                    + (resp != null ? resp.status() : "null"));
        }

        Map<String, HtxBatchResp.Tick> bySymbol = resp.ticks().stream()
                .collect(Collectors.toMap(HtxBatchResp.Tick::contractCode,
                        Function.identity(),
                        (a, b) -> a));

        long now = System.currentTimeMillis();

        return instruments.stream()
                .map(inst -> {
                    HtxBatchResp.Tick t = bySymbol.get(ensureSymbol(inst));
                    if (t == null) return emptyTicker(inst);

                    double bid = (t.bid() != null && t.bid().length > 0)
                            ? t.bid()[0]
                            : Double.NaN;

                    double ask = (t.ask() != null && t.ask().length > 0)
                            ? t.ask()[0]
                            : Double.NaN;

                    return new TickerData(
                            inst,
                            bd(t.close()),
                            bd(String.valueOf(bid)),
                            bd(String.valueOf(ask)),
                            bd(t.high()),
                            bd(t.low()),
                            bd(t.vol()),
                            now
                    );
                })
                .toList();
    }

    private TickerData emptyTicker(InstrumentData inst) {
        return new TickerData(inst, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, System.currentTimeMillis());
    }
}
