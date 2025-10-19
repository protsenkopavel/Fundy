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
import net.protsenko.fundy.app.utils.ExchangeUtils;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static net.protsenko.fundy.app.utils.ExchangeUtils.toBigDecimal;

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
                (inst, t) -> {
                    String priceChangePercent = t.priceChangePercent();

                    if (priceChangePercent != null) {
                        priceChangePercent = priceChangePercent.trim();
                        if (priceChangePercent.endsWith("%")) {
                            priceChangePercent = priceChangePercent.substring(0, priceChangePercent.length() - 1);
                        }
                    }

                    boolean needsCalculation = priceChangePercent == null ||
                            priceChangePercent.isEmpty() ||
                            "0".equals(priceChangePercent) ||
                            "0.0".equals(priceChangePercent) ||
                            "0.00".equals(priceChangePercent);

                    if (needsCalculation) {
                        try {
                            BigDecimal lastPrice = ExchangeUtils.toBigDecimal(t.lastPrice());
                            BigDecimal openPrice = ExchangeUtils.toBigDecimal(t.openPrice());

                            if (lastPrice != null && openPrice != null &&
                                    openPrice.compareTo(BigDecimal.ZERO) != 0 &&
                                    !lastPrice.equals(openPrice)) {

                                BigDecimal change = lastPrice.subtract(openPrice)
                                        .divide(openPrice, 6, BigDecimal.ROUND_HALF_UP)
                                        .multiply(new BigDecimal("100"));
                                priceChangePercent = change.toString();
                            } else {
                                BigDecimal highPrice = ExchangeUtils.toBigDecimal(t.highPrice());
                                BigDecimal lowPrice = ExchangeUtils.toBigDecimal(t.lowPrice());

                                if (lastPrice != null && highPrice != null && lowPrice != null &&
                                        highPrice.compareTo(BigDecimal.ZERO) > 0 &&
                                        lowPrice.compareTo(BigDecimal.ZERO) > 0 &&
                                        highPrice.compareTo(lowPrice) != 0) {

                                    BigDecimal midPrice = highPrice.add(lowPrice).divide(new BigDecimal("2"));
                                    if (midPrice.compareTo(BigDecimal.ZERO) != 0) {
                                        BigDecimal change = lastPrice.subtract(midPrice)
                                                .divide(midPrice, 6, BigDecimal.ROUND_HALF_UP)
                                                .multiply(new BigDecimal("100"));
                                        priceChangePercent = change.toString();
                                    }
                                }
                            }
                        } catch (Exception e) {
                            priceChangePercent = null;
                        }
                    }

                    BigDecimal volCoins = toBigDecimal(t.volume());
                    BigDecimal price = toBigDecimal(t.lastPrice());
                    BigDecimal volUsdt = volCoins.multiply(price);

                    return ticker(inst, t.lastPrice(), t.bidPrice(), t.askPrice(),
                            t.highPrice(), t.lowPrice(), volUsdt.toString(),
                            null, priceChangePercent);
                });
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