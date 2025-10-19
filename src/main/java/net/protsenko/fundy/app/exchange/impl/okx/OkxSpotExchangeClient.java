package net.protsenko.fundy.app.exchange.impl.okx;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.protsenko.fundy.app.domain.InstrumentType;
import net.protsenko.fundy.app.dto.rs.InstrumentData;
import net.protsenko.fundy.app.dto.rs.TickerData;
import net.protsenko.fundy.app.dto.rs.WithdrawalDepositStatus;
import net.protsenko.fundy.app.dto.rs.DepositWithdrawStatus;
import net.protsenko.fundy.app.exchange.ExchangeType;
import net.protsenko.fundy.app.exchange.SpotExchangeClient;
import net.protsenko.fundy.app.exchange.support.ExchangeMappingSupport;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class OkxSpotExchangeClient implements SpotExchangeClient, ExchangeMappingSupport {

    private final OkxCache cache;

    @Override
    public List<InstrumentData> getSpotInstruments() {
        return cache.spotInstruments().stream()
                .filter(i -> "SPOT".equalsIgnoreCase(i.instType()))
                .filter(i -> "live".equalsIgnoreCase(i.state()))
                .map(i -> instrument(i.baseCcy(), i.quoteCcy(), InstrumentType.SPOT, i.instId()))
                .toList();
    }

    @Override
    public List<TickerData> getSpotTickers(List<InstrumentData> instruments) {
        Map<String, OkxTickerItem> byCanonical = cache.spotTickers();
        return mapTickersByCanonical(instruments, byCanonical,
                (inst, t) -> ticker(inst, t.last(), t.bidPx(), t.askPx(),
                        t.high24h(), t.low24h(), t.volCcy24h()));
    }

    @Override
    public ExchangeType getExchangeType() {
        return ExchangeType.OKX;
    }

    @Override
    public Boolean isEnabled() {
        return true;
    }

    @Override
    public WithdrawalDepositStatus getWithdrawalDepositStatus(String asset) {
        return new WithdrawalDepositStatus(
                getExchangeType(),
                asset,
                DepositWithdrawStatus.UNKNOWN,
                DepositWithdrawStatus.UNKNOWN,
                System.currentTimeMillis()
        );
    }
}