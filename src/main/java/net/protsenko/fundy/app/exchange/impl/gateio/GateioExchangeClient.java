package net.protsenko.fundy.app.exchange.impl.gateio;


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
public class GateioExchangeClient implements ExchangeClient, ExchangeMappingSupport {

    private final GateioCache cache;

    @Override
    public List<InstrumentData> getInstruments() {
        return cache.contracts().values().stream()
                .filter(c -> "trading".equalsIgnoreCase(c.status()))
                .map(c -> {
                    String sym = c.name();
                    String[] p = sym.split("_");
                    String base = p.length > 0 ? p[0] : "";
                    String quote = p.length > 1 ? p[1] : "USDT";
                    return instrument(base, quote, InstrumentType.PERPETUAL, sym);
                })
                .toList();
    }

    @Override
    public TickerData getTicker(InstrumentData instrument) {
        Map<String, GateioTickerItem> byCanonical = cache.tickers();
        return mapTickersByCanonical(List.of(instrument), byCanonical,
                (inst, t) -> ticker(inst, t.last(), t.highestBid(), t.lowestAsk(),
                        t.high24h(), t.low24h(), t.volume24h()))
                .stream().findFirst().orElseThrow(() ->
                        new ExchangeException("[" + getExchangeType() + "] ticker not found for " + instrument.baseAsset() + "/" + instrument.quoteAsset()));
    }

    @Override
    public List<TickerData> getTickers(List<InstrumentData> instruments) {
        Map<String, GateioTickerItem> byCanonical = cache.tickers();
        return mapTickersByCanonical(instruments, byCanonical,
                (inst, t) -> ticker(inst, t.last(), t.highestBid(), t.lowestAsk(),
                        t.high24h(), t.low24h(), t.volume24h()));
    }

    @Override
    public FundingRateData getFundingRate(InstrumentData instrument) {
        Map<String, GateioContractItem> byCanonical = cache.contracts();
        return mapFundingByCanonical(List.of(instrument), byCanonical,
                (inst, c) -> funding(inst, c.fundingRate(), c.fundingNextApply() * 1000L))
                .stream().findFirst().orElseThrow(() ->
                        new ExchangeException("[" + getExchangeType() + "] funding not found for " + instrument.baseAsset() + "/" + instrument.quoteAsset()));
    }

    @Override
    public List<FundingRateData> getFundingRates(List<InstrumentData> instruments) {
        Map<String, GateioContractItem> byCanonical = cache.contracts();
        return mapFundingByCanonical(instruments, byCanonical,
                (inst, c) -> funding(inst, c.fundingRate(), c.fundingNextApply() * 1000L));
    }

    @Override
    public ExchangeType getExchangeType() {
        return ExchangeType.GATEIO;
    }

    @Override
    public Boolean isEnabled() {
        return true;
    }
}
