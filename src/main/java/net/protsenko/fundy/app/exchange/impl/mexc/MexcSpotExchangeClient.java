package net.protsenko.fundy.app.exchange.impl.mexc;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.protsenko.fundy.app.domain.InstrumentType;
import net.protsenko.fundy.app.dto.rs.DepositWithdrawStatus;
import net.protsenko.fundy.app.dto.rs.InstrumentData;
import net.protsenko.fundy.app.dto.rs.TickerData;
import net.protsenko.fundy.app.dto.rs.WithdrawalDepositStatus;
import net.protsenko.fundy.app.exchange.ExchangeType;
import net.protsenko.fundy.app.exchange.SpotExchangeClient;
import net.protsenko.fundy.app.exchange.support.ExchangeMappingSupport;
import net.protsenko.fundy.app.props.MexcConfig;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static net.protsenko.fundy.app.utils.ExchangeUtils.toBigDecimal;

@Slf4j
@Component
@RequiredArgsConstructor
public class MexcSpotExchangeClient implements SpotExchangeClient, ExchangeMappingSupport {

    private final MexcCache cache;
    private final MexcConfig config;

    @Override
    public List<InstrumentData> getSpotInstruments() {
        return cache.spotInstruments().stream()
                .filter(i -> i.state() == 0)
                .map(i -> instrument(i.baseCoin(), i.quoteCoin(), InstrumentType.SPOT, i.symbol()))
                .toList();
    }

    @Override
    public List<TickerData> getSpotTickers(List<InstrumentData> instruments) {
        Map<String, MexcTickerItem> byCanonical = cache.spotTickers();
        return mapTickersByCanonical(instruments, byCanonical,
                (inst, t) -> {
                    BigDecimal volCoins = toBigDecimal(t.volume24());
                    BigDecimal price = toBigDecimal(t.lastPrice());
                    BigDecimal volUsdt = volCoins.multiply(price);

                    return ticker(inst, t.lastPrice(), t.bid1Price(), t.ask1Price(),
                            t.high24Price(), t.low24Price(), volUsdt.toString());
                });
    }

    @Override
    public ExchangeType getExchangeType() {
        return ExchangeType.MEXC;
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