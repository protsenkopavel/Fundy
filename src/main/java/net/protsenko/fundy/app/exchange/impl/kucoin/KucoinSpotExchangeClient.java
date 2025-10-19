package net.protsenko.fundy.app.exchange.impl.kucoin;

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
import net.protsenko.fundy.app.props.KucoinConfig;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static net.protsenko.fundy.app.utils.ExchangeUtils.toBigDecimal;

@Slf4j
@Component
@RequiredArgsConstructor
public class KucoinSpotExchangeClient implements SpotExchangeClient, ExchangeMappingSupport {

    private final KucoinCache cache;
    private final KucoinConfig config;

    @Override
    public List<InstrumentData> getSpotInstruments() {
        return cache.spotInstruments().values().stream()
                .map(i -> instrument(i.baseCurrency(), i.quoteCurrency(), InstrumentType.SPOT, i.symbol()))
                .toList();
    }

    @Override
    public List<TickerData> getSpotTickers(List<InstrumentData> instruments) {
        Map<String, KucoinTickerData> byTickers = cache.spotTickers();
        return mapTickersByCanonical(instruments, byTickers,
                (inst, t) -> {
                    BigDecimal volCoins = toBigDecimal(t.vol());
                    BigDecimal price = toBigDecimal(t.price());
                    BigDecimal volUsdt = volCoins.multiply(price);

                    return ticker(inst, t.price(), t.bestBidPrice(), t.bestAskPrice(),
                            t.high(), t.low(), volUsdt.toString());
                });
    }

    @Override
    public ExchangeType getExchangeType() {
        return ExchangeType.KUCOIN;
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