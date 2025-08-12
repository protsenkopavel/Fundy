package net.protsenko.fundy.app.exchange.impl.okx;

import com.fasterxml.jackson.core.type.TypeReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.protsenko.fundy.app.dto.InstrumentType;
import net.protsenko.fundy.app.dto.rs.FundingRateData;
import net.protsenko.fundy.app.dto.rs.InstrumentData;
import net.protsenko.fundy.app.dto.rs.TickerData;
import net.protsenko.fundy.app.exception.ExchangeException;
import net.protsenko.fundy.app.exchange.ExchangeClient;
import net.protsenko.fundy.app.exchange.ExchangeType;
import net.protsenko.fundy.app.exchange.support.ExchangeMappingSupport;
import net.protsenko.fundy.app.props.OkxConfig;
import net.protsenko.fundy.app.utils.HttpExecutor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static net.protsenko.fundy.app.utils.ExchangeUtils.toLong;

@Slf4j
@Component
@RequiredArgsConstructor
public class OkxExchangeClient implements ExchangeClient, ExchangeMappingSupport {

    private final HttpExecutor httpExecutor;
    private final OkxConfig config;
    private final ExecutorService executorService = Executors.newFixedThreadPool(10);

    @Override
    public List<InstrumentData> getInstruments() {
        String url = config.getBaseUrl() + "/api/v5/public/instruments?instType=SWAP";
        OkxResponse<OkxInstrumentItem> resp =
                httpExecutor.get(url, config.getTimeout(), new TypeReference<>() {
                });

        require(resp != null && "0".equals(resp.code()) && resp.data() != null,
                () -> "OKX instruments error: " + (resp != null ? resp.msg() : "null"));

        return resp.data().stream()
                .filter(i -> "SWAP".equalsIgnoreCase(i.instType()))
                .filter(i -> "live".equalsIgnoreCase(i.state()))
                .map(i -> {
                    String[] p = i.instId().split("-");
                    String base = p.length > 0 ? p[0] : "";
                    String quote = p.length > 1 ? p[1] : "";
                    return instrument(base, quote, InstrumentType.PERPETUAL, i.instId());
                })
                .toList();
    }

    @Override
    public TickerData getTicker(InstrumentData instrument) {
        String url = config.getBaseUrl() + "/api/v5/market/tickers?instType=SWAP";
        OkxResponse<OkxTickerItem> resp =
                httpExecutor.get(url, config.getTimeout(), new TypeReference<>() {
                });

        require(resp != null && "0".equals(resp.code()) && resp.data() != null,
                () -> "OKX all-tickers error: " + (resp != null ? resp.msg() : "null"));

        Map<String, OkxTickerItem> byCanonical = indexByCanonical(resp.data(), OkxTickerItem::instId);

        return mapTickersByCanonical(
                List.of(instrument),
                byCanonical,
                (inst, t) -> ticker(
                        inst,
                        t.last(),
                        t.bidPx(),
                        t.askPx(),
                        t.high24h(),
                        t.low24h(),
                        t.vol24h()
                )
        ).stream().findFirst().orElseThrow(() ->
                new ExchangeException("[" + getExchangeType() + "] ticker not found for "
                        + instrument.baseAsset() + "/" + instrument.quoteAsset()));
    }

    @Override
    public List<TickerData> getTickers(List<InstrumentData> instruments) {
        String url = config.getBaseUrl() + "/api/v5/market/tickers?instType=SWAP";
        OkxResponse<OkxTickerItem> resp =
                httpExecutor.get(url, config.getTimeout(), new TypeReference<>() {
                });

        require(resp != null && "0".equals(resp.code()) && resp.data() != null,
                () -> "OKX all-tickers error: " + (resp != null ? resp.msg() : "null"));

        Map<String, OkxTickerItem> byCanonical = indexByCanonical(resp.data(), OkxTickerItem::instId);

        return mapTickersByCanonical(
                instruments,
                byCanonical,
                (inst, t) -> ticker(
                        inst,
                        t.last(),
                        t.bidPx(),
                        t.askPx(),
                        t.high24h(),
                        t.low24h(),
                        t.vol24h()
                )
        );
    }

    @Override
    public FundingRateData getFundingRate(InstrumentData instrument) {
        String instId = ensureSymbol(instrument, () -> instrument.baseAsset() + "-" + instrument.quoteAsset() + "-SWAP");
        String url = config.getBaseUrl() + "/api/v5/public/funding-rate?instId=" + instId;
        OkxResponse<OkxFundingItem> resp =
                httpExecutor.get(url, config.getTimeout(), new TypeReference<>() {
                });

        require(resp != null && "0".equals(resp.code()) && resp.data() != null && !resp.data().isEmpty(),
                () -> "OKX funding error for " + instId + ": " + (resp != null ? resp.msg() : "null"));

        OkxFundingItem f = resp.data().getFirst();
        return funding(instrument, f.fundingRate(), toLong(f.nextFundingTime()));
    }

    @Override
    public List<FundingRateData> getFundingRates(List<InstrumentData> instruments) {
        List<CompletableFuture<FundingRateData>> futures = instruments.stream()
                .map(inst -> CompletableFuture.supplyAsync(() -> {
                    try {
                        return getFundingRate(inst);
                    } catch (Exception e) {
                        log.warn("[OKX] funding fetch failed for {}/{}", inst.baseAsset(), inst.quoteAsset(), e);
                        return null;
                    }
                }, executorService))
                .toList();

        return futures.stream()
                .map(CompletableFuture::join)
                .filter(Objects::nonNull)
                .toList();
    }

    @Override
    public ExchangeType getExchangeType() {
        return ExchangeType.OKX;
    }

    @Override
    public Boolean isEnabled() {
        return config.isEnabled();
    }
}
