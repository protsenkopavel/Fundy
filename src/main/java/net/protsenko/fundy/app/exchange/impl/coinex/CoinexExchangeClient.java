package net.protsenko.fundy.app.exchange.impl.coinex;

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
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class CoinexExchangeClient implements ExchangeClient, ExchangeMappingSupport {

    private final CoinexCache cache;

    @Override
    public List<InstrumentData> getInstruments() {
        return cache.contracts().stream()
                .filter(CoinexContractItem::available)
                .filter(i -> i.type() == 1)
                .map(c -> instrument(c.stock(), c.money(), InstrumentType.PERPETUAL, c.name()))
                .toList();
    }

    @Override
    public TickerData getTicker(InstrumentData instrument) {
        Map<String, Map.Entry<String, CoinexTickerItem>> byCanonical = cache.allTickers();
        return mapTickersByCanonical(List.of(instrument), byCanonical,
                (inst, e) -> {
                    var t = e.getValue();
                    return ticker(inst, t.last(), t.buy(), t.sell(), t.high(), t.low(), t.vol());
                }).stream().findFirst().orElseThrow(() ->
                new ExchangeException("[" + getExchangeType() + "] ticker not found for " + instrument.baseAsset() + "/" + instrument.quoteAsset()));
    }

    @Override
    public List<TickerData> getTickers(List<InstrumentData> instruments) {
        Map<String, Map.Entry<String, CoinexTickerItem>> byCanonical = cache.allTickers();
        return mapTickersByCanonical(instruments, byCanonical,
                (inst, e) -> {
                    var t = e.getValue();
                    return ticker(inst, t.last(), t.buy(), t.sell(), t.high(), t.low(), t.vol());
                });
    }

    @Override
    public FundingRateData getFundingRate(InstrumentData instrument) {
        Map<String, Map.Entry<String, CoinexTickerItem>> byCanonical = cache.allTickers();
        return mapFundingByCanonical(List.of(instrument), byCanonical,
                (inst, e) -> {
                    var t = e.getValue();
                    return funding(inst, t.fundingRateLast(), calcNextFundingMs(t.fundingTime()));
                }).stream().findFirst().orElseThrow(() ->
                new ExchangeException("[" + getExchangeType() + "] funding not found for " + instrument.baseAsset() + "/" + instrument.quoteAsset()));
    }

    @Override
    public List<FundingRateData> getFundingRates(List<InstrumentData> instruments) {
        Map<String, Map.Entry<String, CoinexTickerItem>> byCanonical = cache.allTickers();
        return mapFundingByCanonical(instruments, byCanonical,
                (inst, e) -> {
                    var t = e.getValue();
                    return funding(inst, t.fundingRateLast(), calcNextFundingMs(t.fundingTime()));
                });
    }

    @Override
    public ExchangeType getExchangeType() {
        return ExchangeType.COINEX;
    }

    @Override
    public Boolean isEnabled() {
        return true;
    }

    private long calcNextFundingMs(long fundingTimeField) {
        long now = System.currentTimeMillis();
        if (fundingTimeField <= 0) return now;
        long millis = fundingTimeField < 3600 ? fundingTimeField * 60_000L : fundingTimeField * 1000L;
        return now + millis;
    }
}