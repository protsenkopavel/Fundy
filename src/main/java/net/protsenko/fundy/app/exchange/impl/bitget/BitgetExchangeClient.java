package net.protsenko.fundy.app.exchange.impl.bitget;

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
    public TickerData getTicker(InstrumentData instrument) {
        Map<String, BitgetTickerItem> byCanonical = cache.tickers();
        return mapTickersByCanonical(List.of(instrument), byCanonical,
                (inst, t) -> ticker(inst, t.last(), t.bestBid(), t.bestAsk(), t.high24h(), t.low24h(), t.baseVolume()))
                .stream().findFirst().orElseThrow(() ->
                        new ExchangeException("[" + getExchangeType() + "] ticker not found for " + instrument.baseAsset() + "/" + instrument.quoteAsset()));
    }

    @Override
    public List<TickerData> getTickers(List<InstrumentData> instruments) {
        Map<String, BitgetTickerItem> byCanonical = cache.tickers();
        return mapTickersByCanonical(instruments, byCanonical,
                (inst, t) -> ticker(inst, t.last(), t.bestBid(), t.bestAsk(), t.high24h(), t.low24h(), t.baseVolume()));
    }

    @Override
    public FundingRateData getFundingRate(InstrumentData instrument) {
        Map<String, BitgetTickerItem> byCanonical = cache.tickers();
        long next = nextFundingAlignedHours(8);
        return mapFundingByCanonical(List.of(instrument), byCanonical,
                (inst, t) -> funding(inst, t.fundingRate(), next))
                .stream().findFirst().orElseThrow(() ->
                        new ExchangeException("[" + getExchangeType() + "] funding not found for " + instrument.baseAsset() + "/" + instrument.quoteAsset()));
    }

    @Override
    public List<FundingRateData> getFundingRates(List<InstrumentData> instruments) {
        Map<String, BitgetTickerItem> byCanonical = cache.tickers();
        long next = nextFundingAlignedHours(8);
        return mapFundingByCanonical(instruments, byCanonical,
                (inst, t) -> funding(inst, t.fundingRate(), next));
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
