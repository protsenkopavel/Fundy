package net.protsenko.fundy.app.exchange.impl.bybit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.protsenko.fundy.app.domain.InstrumentType;
import net.protsenko.fundy.app.dto.rs.InstrumentData;
import net.protsenko.fundy.app.dto.rs.TickerData;
import net.protsenko.fundy.app.exchange.ExchangeType;
import net.protsenko.fundy.app.exchange.SpotExchangeClient;
import net.protsenko.fundy.app.exchange.support.ExchangeMappingSupport;
import net.protsenko.fundy.app.props.BybitConfig;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class BybitSpotExchangeClient implements SpotExchangeClient, ExchangeMappingSupport {

    private final BybitCache cache;
    private final BybitConfig config;

    @Override
    public List<InstrumentData> getSpotInstruments() {
        return cache.spotInstruments().stream()
                .filter(i -> "Trading".equalsIgnoreCase(i.status()))
                .map(i -> instrument(i.baseCoin(), i.quoteCoin(), InstrumentType.SPOT, i.symbol()))
                .toList();
    }

    @Override
    public List<TickerData> getSpotTickers(List<InstrumentData> instruments) {
        Map<String, BybitTickerItem> byCanonical = cache.spotTickers();
        return mapTickersByCanonical(instruments, byCanonical,
                (inst, t) -> ticker(inst, t.lastPrice(), t.bid1Price(), t.ask1Price(),
                        t.highPrice24h(), t.lowPrice24h(), t.volume24h()));
    }

    @Override
    public ExchangeType getExchangeType() {
        return ExchangeType.BYBIT;
    }

    @Override
    public Boolean isEnabled() {
        return config.isEnabled();
    }
}