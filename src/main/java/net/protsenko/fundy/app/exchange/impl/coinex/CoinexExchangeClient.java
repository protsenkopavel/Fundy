package net.protsenko.fundy.app.exchange.impl.coinex;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.protsenko.fundy.app.domain.InstrumentType;
import net.protsenko.fundy.app.dto.rs.FundingRateData;
import net.protsenko.fundy.app.dto.rs.InstrumentData;
import net.protsenko.fundy.app.dto.rs.TickerData;
import net.protsenko.fundy.app.exchange.ExchangeClient;
import net.protsenko.fundy.app.exchange.ExchangeType;
import net.protsenko.fundy.app.exchange.support.ExchangeMappingSupport;
import net.protsenko.fundy.app.props.CoinexConfig;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

import static net.protsenko.fundy.app.utils.SymbolNormalizer.canonicalKey;

@Slf4j
@Component
@RequiredArgsConstructor
public class CoinexExchangeClient implements ExchangeClient, ExchangeMappingSupport {

    private final CoinexCache cache;
    private final CoinexConfig config;

    @Override
    public List<InstrumentData> getFuturesInstruments() {
        return cache.contracts().stream()
                .filter(CoinexContractItem::available)
                .filter(i -> i.type() == 1)
                .map(c -> instrument(c.stock(), c.money(), InstrumentType.PERPETUAL, c.name()))
                .toList();
    }

    @Override
    public List<TickerData> getFuturesTickers(List<InstrumentData> instruments) {
        Map<String, Map.Entry<String, CoinexTickerItem>> byCanonical = cache.allTickers();
        return mapTickersByCanonical(instruments, byCanonical,
                (inst, e) -> {
                    var t = e.getValue();
                    return ticker(inst, t.last(), t.buy(), t.sell(), t.high(), t.low(), t.vol());
                });
    }

    @Override
    public List<FundingRateData> getFundingRates(List<InstrumentData> instruments) {
        Map<String, Map.Entry<String, CoinexTickerItem>> byCanonical = cache.allTickers();
        Map<String, CoinexFundingMeta> metaByCanonical = cache.fundingMeta();
        return mapFundingByCanonical(instruments, byCanonical, (inst, e) -> {
            var t = e.getValue();
            long next = resolveNextFundingTs(inst, t, metaByCanonical);
            return funding(inst, t.fundingRateLast(), next);
        });
    }

    private long resolveNextFundingTs(InstrumentData inst,
                                      CoinexTickerItem t,
                                      Map<String, CoinexFundingMeta> metaByCanonical) {
        long now = System.currentTimeMillis();
        long fromTicker = calcNextFundingMs(t.fundingTime());
        String key = canonicalKey(inst);
        CoinexFundingMeta meta = metaByCanonical.get(key);
        long fromMeta = (meta != null ? meta.nextFundingTime() : 0L);

        final long TWENTY_MIN = 20L * 60_000L;
        final boolean useTicker =
                (fromMeta <= now) || (Math.abs(fromMeta - fromTicker) > TWENTY_MIN);

        long candidate = useTicker ? fromTicker : fromMeta;
        return normalizeFundingBoundary(candidate);
    }

    private long calcNextFundingMs(long fundingTimeMinutes) {
        long now = System.currentTimeMillis();
        if (fundingTimeMinutes <= 0) return now;
        return now + fundingTimeMinutes * 60_000L;
    }

    private long normalizeFundingBoundary(long ts) {
        final long ONE_MIN = 60_000L;
        final long ONE_HOUR = 3_600_000L;
        final long EPS = 120_000L;

        if (ts % ONE_MIN != 0) {
            ts = ((ts + ONE_MIN - 1) / ONE_MIN) * ONE_MIN;
        }

        long mod = ts % ONE_HOUR;
        if (mod <= EPS) {
            ts -= mod;
        } else if (ONE_HOUR - mod <= EPS) {
            ts += (ONE_HOUR - mod);
        }
        return ts;
    }

    @Override
    public ExchangeType getExchangeType() {
        return ExchangeType.COINEX;
    }

    @Override
    public List<InstrumentData> getSpotInstruments() {
        return cache.spotInstruments().values().stream()
                .map(item -> instrument(item.stock(), item.money(), InstrumentType.SPOT, item.name()))
                .toList();
    }

    @Override
    public List<TickerData> getSpotTickers(List<InstrumentData> instruments) {
        Map<String, CoinexSpotTickerItem> byCanonical = cache.spotTickers();
        return mapTickersByCanonical(instruments, byCanonical,
                (inst, ticker) -> ticker(inst, ticker.last(), ticker.buy(), ticker.sell(),
                                       ticker.high(), ticker.low(), ticker.vol()));
    }

    @Override
    public Boolean isEnabled() {
        return config.isEnabled();
    }
}