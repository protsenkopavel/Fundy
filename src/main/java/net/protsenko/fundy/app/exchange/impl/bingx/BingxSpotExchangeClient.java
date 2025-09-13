package net.protsenko.fundy.app.exchange.impl.bingx;

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
import net.protsenko.fundy.app.props.BingxConfig;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class BingxSpotExchangeClient implements SpotExchangeClient, ExchangeMappingSupport {

    private final BingxCache cache;
    private final BingxConfig config;

    @Override
    public List<InstrumentData> getSpotInstruments() {
        var instruments = cache.spotInstruments().values().stream()
                .filter(i -> i.status() != null && i.status() == 1)
                .filter(i -> i.getBaseCurrency() != null && i.getQuoteCurrency() != null)
                .map(i -> instrument(i.getBaseCurrency(), i.getQuoteCurrency(), InstrumentType.SPOT, i.symbol()))
                .toList();
        return instruments;
    }

    @Override
    public List<TickerData> getSpotTickers(List<InstrumentData> instruments) {
        Map<String, BingxSpotTickerItem> byCanonical = cache.spotTickers();
        return mapTickersByCanonical(instruments, byCanonical,
                (inst, t) -> ticker(inst, t.lastPrice(), t.bidPrice(), t.askPrice(),
                        t.highPrice(), t.lowPrice(), t.volume()));
    }

    @Override
    public ExchangeType getExchangeType() {
        return ExchangeType.BINGX;
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
                DepositWithdrawStatus.UNKNOWN,
                DepositWithdrawStatus.UNKNOWN,
                System.currentTimeMillis()
        );
    }
}