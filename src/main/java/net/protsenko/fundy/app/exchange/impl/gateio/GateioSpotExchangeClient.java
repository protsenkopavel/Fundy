package net.protsenko.fundy.app.exchange.impl.gateio;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.protsenko.fundy.app.domain.InstrumentType;
import net.protsenko.fundy.app.dto.rs.InstrumentData;
import net.protsenko.fundy.app.dto.rs.TickerData;
import net.protsenko.fundy.app.dto.rs.WithdrawalDepositStatus;
import net.protsenko.fundy.app.exchange.ExchangeType;
import net.protsenko.fundy.app.exchange.SpotExchangeClient;
import net.protsenko.fundy.app.exchange.support.ExchangeMappingSupport;
import net.protsenko.fundy.app.props.GateioConfig;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class GateioSpotExchangeClient implements SpotExchangeClient, ExchangeMappingSupport {

    private final GateioCache cache;
    private final GateioConfig config;

    @Override
    public List<InstrumentData> getSpotInstruments() {
        return cache.spotInstruments().values().stream()
                .map(i -> instrument(i.base(), i.quote(), InstrumentType.SPOT, i.id()))
                .toList();
    }

    @Override
    public List<TickerData> getSpotTickers(List<InstrumentData> instruments) {
        Map<String, GateioSpotTickerItem> byCanonical = cache.spotTickers();
        return mapTickersByCanonical(instruments, byCanonical,
                (inst, t) -> ticker(inst, t.last(), t.highestBid(), t.lowestAsk(),
                        t.high24h(), t.low24h(), t.baseVolume()));
    }

    @Override
    public ExchangeType getExchangeType() {
        return ExchangeType.GATEIO;
    }

    @Override
    public Boolean isEnabled() {
        return config.isEnabled();
    }

    @Override
    public WithdrawalDepositStatus getWithdrawalDepositStatus(String asset) {
        return new WithdrawalDepositStatus(
                getExchangeType(),
                asset,
                true,
                true,
                System.currentTimeMillis()
        );
    }
}