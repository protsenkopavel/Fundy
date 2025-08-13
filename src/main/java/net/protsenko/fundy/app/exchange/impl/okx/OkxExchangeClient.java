package net.protsenko.fundy.app.exchange.impl.okx;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.protsenko.fundy.app.dto.InstrumentType;
import net.protsenko.fundy.app.dto.rs.FundingRateData;
import net.protsenko.fundy.app.dto.rs.InstrumentData;
import net.protsenko.fundy.app.dto.rs.TickerData;
import net.protsenko.fundy.app.exchange.ExchangeClient;
import net.protsenko.fundy.app.exchange.ExchangeType;
import net.protsenko.fundy.app.exchange.support.ExchangeMappingSupport;
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

    private final OkxCache cache;
    private final ExecutorService executor = Executors.newFixedThreadPool(10);

    @Override
    public List<InstrumentData> getInstruments() {
        return cache.instruments().stream()
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
    public List<TickerData> getTickers(List<InstrumentData> instruments) {
        Map<String, OkxTickerItem> byCanonical = cache.tickers();
        return mapTickersByCanonical(instruments, byCanonical,
                (inst, t) -> ticker(inst, t.last(), t.bidPx(), t.askPx(), t.high24h(), t.low24h(), t.vol24h()));
    }

    @Override
    public List<FundingRateData> getFundingRates(List<InstrumentData> instruments) {
        List<CompletableFuture<FundingRateData>> futures = instruments.stream()
                .map(inst -> CompletableFuture.supplyAsync(() -> {
                    try {
                        String instId = ensureSymbol(inst, inst.baseAsset() + "-" + inst.quoteAsset() + "-SWAP");
                        OkxFundingItem f = cache.fundingSingle(instId);
                        return funding(inst, f.fundingRate(), toLong(f.nextFundingTime()));
                    } catch (Exception e) {
                        log.warn("[OKX] funding fetch failed for {}/{}", inst.baseAsset(), inst.quoteAsset(), e);
                        return null;
                    }
                }, executor))
                .toList();

        return futures.stream().map(CompletableFuture::join).filter(Objects::nonNull).toList();
    }

    @Override
    public ExchangeType getExchangeType() {
        return ExchangeType.OKX;
    }

    @Override
    public Boolean isEnabled() {
        return true;
    }
}
