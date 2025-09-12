package net.protsenko.fundy.app.exchange.impl.htx;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.protsenko.fundy.app.domain.InstrumentType;
import net.protsenko.fundy.app.dto.rs.InstrumentData;
import net.protsenko.fundy.app.dto.rs.TickerData;
import net.protsenko.fundy.app.exchange.ExchangeType;
import net.protsenko.fundy.app.exchange.SpotExchangeClient;
import net.protsenko.fundy.app.exchange.support.ExchangeMappingSupport;
import net.protsenko.fundy.app.props.HtxConfig;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class HtxSpotExchangeClient implements SpotExchangeClient, ExchangeMappingSupport {

    private final HtxCache cache;
    private final HtxConfig config;

    @Override
    public List<InstrumentData> getSpotInstruments() {
        return cache.spotInstruments().values().stream()
                .filter(i -> "online".equals(i.state()))
                .filter(i -> i.getBaseCurrency() != null && i.getQuoteCurrency() != null)
                .map(i -> instrument(i.getBaseCurrency(), i.getQuoteCurrency(), InstrumentType.SPOT, i.symbol()))
                .toList();
    }

    @Override
    public List<TickerData> getSpotTickers(List<InstrumentData> instruments) {
        Map<String, HtxSpotTickerItem> byCanonical = cache.spotTickers();
        return mapTickersByCanonical(instruments, byCanonical,
                (inst, t) -> ticker(inst, t.close(), t.bid(), t.ask(),
                        t.high(), t.low(), t.vol()));
    }

    @Override
    public ExchangeType getExchangeType() {
        return ExchangeType.HTX;
    }

    @Override
    public Boolean isEnabled() {
        return config.isEnabled();
    }
}