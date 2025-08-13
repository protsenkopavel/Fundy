package net.protsenko.fundy.app.exchange.impl.bingx;

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

@Slf4j
@Component
@RequiredArgsConstructor
public class BingxExchangeClient implements ExchangeClient, ExchangeMappingSupport {

    private final BingxCache cache;

    @Override
    public List<InstrumentData> getInstruments() {
        return cache.contracts().stream()
                .filter(c -> c.status() == 1)
                .map(c -> instrument(c.asset(), c.currency(), InstrumentType.PERPETUAL, c.symbol()))
                .toList();
    }

    @Override
    public List<TickerData> getTickers(List<InstrumentData> instruments) {
        Map<String, BingxTickerItem> byCanonical = cache.tickers();
        return mapTickersByCanonical(instruments, byCanonical,
                (inst, t) -> ticker(inst, t.lastPrice(), t.bestBid(), t.bestAsk(), t.high24h(), t.low24h(), t.volume24h()));
    }

    @Override
    public List<FundingRateData> getFundingRates(List<InstrumentData> instruments) {
        Map<String, BingxPremiumIndexItem> byCanonical = cache.funding();
        return mapFundingByCanonical(instruments, byCanonical,
                (inst, f) -> funding(inst, f.lastFundingRate(), f.nextFundingTime()));
    }

    @Override
    public ExchangeType getExchangeType() {
        return ExchangeType.BINGX;
    }

    @Override
    public Boolean isEnabled() {
        return true;
    }
}
