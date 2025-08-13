package net.protsenko.fundy.app.exchange.impl.bybit;

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

import static net.protsenko.fundy.app.utils.ExchangeUtils.toLong;


@Slf4j
@Component
@RequiredArgsConstructor
public class BybitExchangeClient implements ExchangeClient, ExchangeMappingSupport {

    private final BybitCache cache;

    @Override
    public List<InstrumentData> getInstruments() {
        return cache.instruments().stream()
                .filter(i -> "Trading".equalsIgnoreCase(i.status()))
                .map(i -> instrument(i.baseCoin(), i.quoteCoin(), InstrumentType.PERPETUAL, i.symbol()))
                .toList();
    }

    @Override
    public List<TickerData> getTickers(List<InstrumentData> instruments) {
        Map<String, BybitTickerItem> byCanonical = cache.tickers();
        return mapTickersByCanonical(instruments, byCanonical,
                (inst, t) -> ticker(inst, t.lastPrice(), t.bid1Price(), t.ask1Price(),
                        t.highPrice24h(), t.lowPrice24h(), t.volume24h()));
    }

    @Override
    public List<FundingRateData> getFundingRates(List<InstrumentData> instruments) {
        Map<String, BybitTickerItem> byCanonical = cache.tickers();
        return mapFundingByCanonical(instruments, byCanonical,
                (inst, t) -> funding(inst, t.fundingRate(), toLong(t.nextFundingTime())));
    }

    @Override
    public ExchangeType getExchangeType() {
        return ExchangeType.BYBIT;
    }

    @Override
    public Boolean isEnabled() {
        return true;
    }
}
