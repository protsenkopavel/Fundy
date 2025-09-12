package net.protsenko.fundy.app.exchange.impl.mexc;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.protsenko.fundy.app.domain.InstrumentType;
import net.protsenko.fundy.app.dto.rs.FundingRateData;
import net.protsenko.fundy.app.dto.rs.InstrumentData;
import net.protsenko.fundy.app.dto.rs.TickerData;
import net.protsenko.fundy.app.exchange.ExchangeType;
import net.protsenko.fundy.app.exchange.FuturesExchangeClient;
import net.protsenko.fundy.app.exchange.support.ExchangeMappingSupport;
import net.protsenko.fundy.app.props.MexcConfig;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

import static net.protsenko.fundy.app.utils.ExchangeUtils.toLong;

@Slf4j
@Component
@RequiredArgsConstructor
public class MexcFuturesExchangeClient implements FuturesExchangeClient, ExchangeMappingSupport {

    private final MexcCache cache;
    private final MexcConfig config;

    @Override
    public List<InstrumentData> getFuturesInstruments() {
        return cache.instruments().stream()
                .filter(i -> i.state() == 0)
                .map(i -> instrument(i.baseCoin(), i.quoteCoin(), InstrumentType.PERPETUAL, i.symbol()))
                .toList();
    }

    @Override
    public List<TickerData> getFuturesTickers(List<InstrumentData> instruments) {
        Map<String, MexcTickerItem> byCanonical = cache.tickers();
        return mapTickersByCanonical(instruments, byCanonical,
                (inst, t) -> ticker(inst, t.lastPrice(), t.bid1Price(), t.ask1Price(),
                        t.high24Price(), t.low24Price(), t.volume24()));
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
        return config.isEnabled();
    }
}