package net.protsenko.fundy.app.exchange.impl.mexc;

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

import static net.protsenko.fundy.app.utils.ExchangeUtils.toLong;


@Slf4j
@Component
@RequiredArgsConstructor
public class MexcExchangeClient implements ExchangeClient, ExchangeMappingSupport {

    private final MexcCache cache;

    @Override
    public List<InstrumentData> getInstruments() {
        return cache.instruments().stream()
                .filter(i -> i.state() == 0)
                .map(i -> instrument(i.baseCoin(), i.quoteCoin(), InstrumentType.PERPETUAL, i.symbol()))
                .toList();
    }

    @Override
    public TickerData getTicker(InstrumentData instrument) {
        Map<String, MexcTickerItem> byCanonical = cache.tickers();
        return mapTickersByCanonical(List.of(instrument), byCanonical,
                (inst, t) -> ticker(inst, t.lastPrice(), t.bid1Price(), t.ask1Price(),
                        t.high24Price(), t.low24Price(), t.volume24()))
                .stream().findFirst().orElseThrow(() ->
                        new ExchangeException("[" + getExchangeType() + "] ticker not found for " + instrument.baseAsset() + "/" + instrument.quoteAsset()));
    }

    @Override
    public List<TickerData> getTickers(List<InstrumentData> instruments) {
        Map<String, MexcTickerItem> byCanonical = cache.tickers();
        return mapTickersByCanonical(instruments, byCanonical,
                (inst, t) -> ticker(inst, t.lastPrice(), t.bid1Price(), t.ask1Price(),
                        t.high24Price(), t.low24Price(), t.volume24()));
    }

    @Override
    public FundingRateData getFundingRate(InstrumentData instrument) {
        Map<String, MexcFundingItem> byCanonical = cache.funding();
        return mapFundingByCanonical(List.of(instrument), byCanonical,
                (inst, f) -> funding(inst, f.fundingRate(), toLong(f.nextSettleTime())))
                .stream().findFirst().orElseThrow(() ->
                        new ExchangeException("[" + getExchangeType() + "] funding not found for " + instrument.baseAsset() + "/" + instrument.quoteAsset()));
    }

    @Override
    public List<FundingRateData> getFundingRates(List<InstrumentData> instruments) {
        Map<String, MexcFundingItem> byCanonical = cache.funding();
        return mapFundingByCanonical(instruments, byCanonical,
                (inst, f) -> funding(inst, f.fundingRate(), toLong(f.nextSettleTime())));
    }

    @Override
    public ExchangeType getExchangeType() {
        return ExchangeType.MEXC;
    }

    @Override
    public Boolean isEnabled() {
        return true;
    }
}
