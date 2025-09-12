package net.protsenko.fundy.app.exchange.impl.gateio;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.protsenko.fundy.app.domain.InstrumentType;
import net.protsenko.fundy.app.dto.rs.FundingRateData;
import net.protsenko.fundy.app.dto.rs.InstrumentData;
import net.protsenko.fundy.app.dto.rs.TickerData;
import net.protsenko.fundy.app.exchange.ExchangeType;
import net.protsenko.fundy.app.exchange.FuturesExchangeClient;
import net.protsenko.fundy.app.exchange.support.ExchangeMappingSupport;
import net.protsenko.fundy.app.props.GateioConfig;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class GateioFuturesExchangeClient implements FuturesExchangeClient, ExchangeMappingSupport {

    private final GateioCache cache;
    private final GateioConfig config;

    @Override
    public List<InstrumentData> getFuturesInstruments() {
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
    public List<TickerData> getFuturesTickers(List<InstrumentData> instruments) {
        Map<String, GateioTickerItem> byCanonical = cache.tickers();
        return mapTickersByCanonical(instruments, byCanonical,
                (inst, t) -> ticker(inst, t.last(), t.highestBid(), t.lowestAsk(),
                        t.high24h(), t.low24h(), t.volume24h()));
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
        return config.isEnabled();
    }
}