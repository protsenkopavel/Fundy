package net.protsenko.fundy.app.exchange.impl.coinex;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.protsenko.fundy.app.domain.InstrumentType;
import net.protsenko.fundy.app.dto.rs.InstrumentData;
import net.protsenko.fundy.app.dto.rs.TickerData;
import net.protsenko.fundy.app.exchange.ExchangeType;
import net.protsenko.fundy.app.exchange.SpotExchangeClient;
import net.protsenko.fundy.app.exchange.support.ExchangeMappingSupport;
import net.protsenko.fundy.app.props.CoinexConfig;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class CoinexSpotExchangeClient implements SpotExchangeClient, ExchangeMappingSupport {

    private final CoinexCache cache;
    private final CoinexConfig config;

    @Override
    public List<InstrumentData> getSpotInstruments() {
        return cache.spotInstruments().values().stream()
                .map(item -> instrument(item.stock(), item.money(), InstrumentType.SPOT, item.name()))
                .toList();
    }

    @Override
    public List<TickerData> getSpotTickers(List<InstrumentData> instruments) {
        Map<String, CoinexSpotTickerItem> byCanonical = cache.spotTickers();
        return mapTickersByCanonical(instruments, byCanonical,
                (inst, ticker) -> ticker(inst, ticker.last(), ticker.buy(), ticker.sell(),
                                       ticker.high(), ticker.low(), ticker.vol()));
    }

    @Override
    public ExchangeType getExchangeType() {
        return ExchangeType.COINEX;
    }

    @Override
    public Boolean isEnabled() {
        return config.isEnabled();
    }
}