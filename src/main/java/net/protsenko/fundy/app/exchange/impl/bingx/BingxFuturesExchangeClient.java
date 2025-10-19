package net.protsenko.fundy.app.exchange.impl.bingx;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.protsenko.fundy.app.domain.InstrumentType;
import net.protsenko.fundy.app.dto.rs.FundingRateData;
import net.protsenko.fundy.app.dto.rs.InstrumentData;
import net.protsenko.fundy.app.dto.rs.TickerData;
import net.protsenko.fundy.app.exchange.ExchangeType;
import net.protsenko.fundy.app.exchange.FuturesExchangeClient;
import net.protsenko.fundy.app.exchange.support.ExchangeMappingSupport;
import net.protsenko.fundy.app.props.BingxConfig;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static net.protsenko.fundy.app.utils.ExchangeUtils.toBigDecimal;

@Slf4j
@Component
@RequiredArgsConstructor
public class BingxFuturesExchangeClient implements FuturesExchangeClient, ExchangeMappingSupport {

    private final BingxCache cache;
    private final BingxConfig config;

    @Override
    public List<InstrumentData> getFuturesInstruments() {
        return cache.contracts().stream()
                .filter(c -> c.status() == 1)
                .map(c -> instrument(c.asset(), c.currency(), InstrumentType.PERPETUAL, c.symbol()))
                .toList();
    }

    @Override
    public List<TickerData> getFuturesTickers(List<InstrumentData> instruments) {
        Map<String, BingxTickerItem> byCanonical = cache.tickers();
        return mapTickersByCanonical(instruments, byCanonical,
                (inst, t) -> {
                    BigDecimal volCoins = toBigDecimal(t.volume24h());
                    BigDecimal price = toBigDecimal(t.lastPrice());
                    BigDecimal volUsdt = volCoins.multiply(price);
                    return ticker(inst, t.lastPrice(), t.bestBid(), t.bestAsk(),
                            t.high24h(), t.low24h(), volUsdt.toString());
                });
    }

    @Override
    public List<FundingRateData> getFundingRates(List<InstrumentData> instruments) {
        Map<String, BingxPremiumIndexItem> byCanonical = cache.funding();
        return mapFundingByCanonical(instruments, byCanonical,
                (inst, f) -> funding(inst, f.lastFundingRate(), f.nextFundingTime()));
    }

    @Override
    public ExchangeType getExchangeType() {
        return ExchangeType.BINGX;
    }

    @Override
    public Boolean isEnabled() {
        return config.isEnabled();
    }
}