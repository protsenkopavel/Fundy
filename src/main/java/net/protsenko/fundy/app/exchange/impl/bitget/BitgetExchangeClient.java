package net.protsenko.fundy.app.exchange.impl.bitget;

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

import static net.protsenko.fundy.app.utils.SymbolNormalizer.canonicalKey;

@Slf4j
@Component
@RequiredArgsConstructor
public class BitgetExchangeClient implements ExchangeClient, ExchangeMappingSupport {

    private final BitgetCache cache;

    @Override
    public List<InstrumentData> getInstruments() {
        return cache.contracts().stream()
                .filter(c -> "normal".equalsIgnoreCase(c.symbolStatus()))
                .map(c -> instrument(c.baseCoin(), c.quoteCoin(), InstrumentType.PERPETUAL, c.symbol()))
                .toList();
    }

    @Override
    public List<TickerData> getTickers(List<InstrumentData> instruments) {
        Map<String, BitgetTickerItem> byCanonical = cache.tickers();
        return mapTickersByCanonical(instruments, byCanonical,
                (inst, t) -> ticker(inst, t.last(), t.bestBid(), t.bestAsk(), t.high24h(), t.low24h(), t.baseVolume()));
    }

    @Override
    public List<FundingRateData> getFundingRates(List<InstrumentData> instruments) {
        Map<String, BitgetTickerItem> byCanonical = cache.tickers();
        Map<String, BitgetFundingMeta> metaByCanonical = cache.fundingMeta();
        return mapFundingByCanonical(instruments, byCanonical, (inst, t) -> {
            long next = resolveNextFundingTs(inst, metaByCanonical);
            return funding(inst, t.fundingRate(), next);
        });
    }

    private long resolveNextFundingTs(InstrumentData inst, Map<String, BitgetFundingMeta> metaByCanonical) {
        String key = canonicalKey(inst);
        BitgetFundingMeta meta = metaByCanonical.get(key);
        if (meta != null && meta.nextUpdate() != null) {
            try {
                return Long.parseLong(meta.nextUpdate());
            } catch (NumberFormatException ignore) { /* fallthrough */ }
        }
        int interval = 8;
        if (meta != null && meta.fundingRateInterval() != null) {
            try {
                interval = Math.max(1, Integer.parseInt(meta.fundingRateInterval()));
            } catch (Exception ignore) {
            }
        }
        return nextFundingAlignedHours(interval);
    }

    @Override
    public ExchangeType getExchangeType() {
        return ExchangeType.BITGET;
    }

    @Override
    public Boolean isEnabled() {
        return true;
    }
}
