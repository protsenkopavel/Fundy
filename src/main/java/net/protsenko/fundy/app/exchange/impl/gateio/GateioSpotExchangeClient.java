package net.protsenko.fundy.app.exchange.impl.gateio;

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
import net.protsenko.fundy.app.props.GateioConfig;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static net.protsenko.fundy.app.utils.ExchangeUtils.toBigDecimal;

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
                (inst, t) -> {
                    String priceChangePercent = t.changePercentage();

                    if (priceChangePercent != null) {
                        priceChangePercent = priceChangePercent.trim();
                        if (priceChangePercent.endsWith("%")) {
                            priceChangePercent = priceChangePercent.substring(0, priceChangePercent.length() - 1);
                        }
                    }

                    BigDecimal volCoins = toBigDecimal(t.baseVolume());
                    BigDecimal price = toBigDecimal(t.last());
                    BigDecimal volUsdt = volCoins.multiply(price);

                    return ticker(inst, t.last(), t.highestBid(), t.lowestAsk(),
                            t.high24h(), t.low24h(), volUsdt.toString(),
                            null, priceChangePercent);
                });
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
                DepositWithdrawStatus.UNKNOWN,
                DepositWithdrawStatus.UNKNOWN,
                System.currentTimeMillis()
        );
    }
}