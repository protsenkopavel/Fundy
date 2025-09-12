package net.protsenko.fundy.app.exchange.impl.htx;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.protsenko.fundy.app.domain.InstrumentType;
import net.protsenko.fundy.app.dto.rs.FundingRateData;
import net.protsenko.fundy.app.dto.rs.InstrumentData;
import net.protsenko.fundy.app.dto.rs.TickerData;
import net.protsenko.fundy.app.exchange.ExchangeType;
import net.protsenko.fundy.app.exchange.FuturesExchangeClient;
import net.protsenko.fundy.app.exchange.support.ExchangeMappingSupport;
import net.protsenko.fundy.app.props.HtxConfig;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

import static net.protsenko.fundy.app.utils.ExchangeUtils.toLong;

@Slf4j
@Component
@RequiredArgsConstructor
public class HtxFuturesExchangeClient implements FuturesExchangeClient, ExchangeMappingSupport {

    private final HtxCache cache;
    private final HtxConfig config;

    @Override
    public List<InstrumentData> getFuturesInstruments() {
        return cache.contracts().stream()
                .filter(c -> c.contractStatus() == 1)
                .map(c -> {
                    String code = c.contractCode();
                    String[] p = code.split("-");
                    String base = p.length > 0 ? p[0] : c.symbol();
                    String quote = p.length > 1 ? p[1] : c.tradePartition();
                    return instrument(base, quote, InstrumentType.PERPETUAL, code);
                })
                .toList();
    }

    @Override
    public List<TickerData> getFuturesTickers(List<InstrumentData> instruments) {
        Map<String, HtxBatchResp.Tick> byCanonical = cache.tickers();
        return mapTickersByCanonical(instruments, byCanonical,
                (inst, t) -> ticker(inst, String.valueOf(t.close()),
                        "0", "0", String.valueOf(t.high()), String.valueOf(t.low()), String.valueOf(t.vol())));
    }

    @Override
    public List<FundingRateData> getFundingRates(List<InstrumentData> instruments) {
        Map<String, HtxFundingItem> byCanonical = cache.funding();
        return mapFundingByCanonical(instruments, byCanonical,
                (inst, f) -> funding(inst, f.fundingRate(), toLong(f.fundingTime())));
    }

    @Override
    public ExchangeType getExchangeType() {
        return ExchangeType.HTX;
    }

    @Override
    public Boolean isEnabled() {
        return config.isEnabled();
    }
}