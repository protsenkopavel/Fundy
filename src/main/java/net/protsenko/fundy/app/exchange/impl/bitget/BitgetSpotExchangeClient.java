package net.protsenko.fundy.app.exchange.impl.bitget;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.protsenko.fundy.app.domain.InstrumentType;
import net.protsenko.fundy.app.dto.rs.InstrumentData;
import net.protsenko.fundy.app.dto.rs.TickerData;
import net.protsenko.fundy.app.exchange.ExchangeType;
import net.protsenko.fundy.app.exchange.SpotExchangeClient;
import net.protsenko.fundy.app.exchange.support.ExchangeMappingSupport;
import net.protsenko.fundy.app.props.BitgetConfig;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class BitgetSpotExchangeClient implements SpotExchangeClient, ExchangeMappingSupport {

    private final BitgetCache cache;
    private final BitgetConfig config;

    @Override
    public List<InstrumentData> getSpotInstruments() {
        return cache.spotInstruments().values().stream()
                .map(i -> instrument(i.baseCoin(), i.quoteCoin(), InstrumentType.SPOT, i.symbol()))
                .toList();
    }

    @Override
    public List<TickerData> getSpotTickers(List<InstrumentData> instruments) {
        Map<String, BitgetSpotTickerItem> byCanonical = cache.spotTickers();
        return mapTickersByCanonical(instruments, byCanonical,
                (inst, t) -> ticker(inst, t.close(), t.bidPr(), t.askPr(),
                        t.high24h(), t.low24h(), t.baseVol()));
    }

    @Override
    public ExchangeType getExchangeType() {
        return ExchangeType.BITGET;
    }

    @Override
    public Boolean isEnabled() {
        return config.isEnabled();
    }
}