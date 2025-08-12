package net.protsenko.fundy.app.exchange.impl.kucoin;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.protsenko.fundy.app.dto.InstrumentType;
import net.protsenko.fundy.app.dto.rs.FundingRateData;
import net.protsenko.fundy.app.dto.rs.InstrumentData;
import net.protsenko.fundy.app.dto.rs.TickerData;
import net.protsenko.fundy.app.exception.ExchangeException;
import net.protsenko.fundy.app.exchange.ExchangeClient;
import net.protsenko.fundy.app.exchange.ExchangeType;
import net.protsenko.fundy.app.exchange.support.ExchangeMappingSupport;
import net.protsenko.fundy.app.utils.SymbolNormalizer;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Component
@RequiredArgsConstructor
public class KucoinExchangeClient implements ExchangeClient, ExchangeMappingSupport {

    private final KucoinCache cache;

    @Override
    public List<InstrumentData> getInstruments() {
        return cache.contracts().values().stream()
                .filter(c -> "Open".equalsIgnoreCase(c.status()))
                .map(c -> instrument(c.baseCurrency(), c.quoteCurrency(), InstrumentType.PERPETUAL, c.symbol()))
                .toList();
    }

    @Override
    public TickerData getTicker(InstrumentData instrument) {
        Map<String, KucoinTickerData> byTickers = cache.tickers();
        Map<String, KucoinContractItem> byContracts = cache.contracts();
        return mapTickersByCanonical(List.of(instrument), byTickers, (inst, t) -> {
            String key = SymbolNormalizer.canonicalKey(inst);
            KucoinContractItem c = byContracts.get(key);
            if (c == null) return null;
            return ticker(inst, t.price(), t.bestBidPrice(), t.bestAskPrice(), c.highPrice(), c.lowPrice(), c.volumeOf24h());
        }).stream().filter(Objects::nonNull).findFirst().orElseThrow(() ->
                new ExchangeException("[" + getExchangeType() + "] ticker not found for " + instrument.baseAsset() + "/" + instrument.quoteAsset()));
    }

    @Override
    public List<TickerData> getTickers(List<InstrumentData> instruments) {
        Map<String, KucoinTickerData> byTickers = cache.tickers();
        Map<String, KucoinContractItem> byContracts = cache.contracts();
        return mapTickersByCanonical(instruments, byTickers, (inst, t) -> {
            String key = SymbolNormalizer.canonicalKey(inst);
            KucoinContractItem c = byContracts.get(key);
            if (c == null) return null;
            return ticker(inst, t.price(), t.bestBidPrice(), t.bestAskPrice(), c.highPrice(), c.lowPrice(), c.volumeOf24h());
        });
    }

    @Override
    public FundingRateData getFundingRate(InstrumentData instrument) {
        Map<String, KucoinContractItem> byContracts = cache.contracts();
        return mapFundingByCanonical(List.of(instrument), byContracts,
                (inst, c) -> funding(inst, c.fundingFeeRate(), c.nextFundingRateDateTime()))
                .stream().findFirst().orElseThrow(() ->
                        new ExchangeException("[" + getExchangeType() + "] funding not found for " + instrument.baseAsset() + "/" + instrument.quoteAsset()));
    }

    @Override
    public List<FundingRateData> getFundingRates(List<InstrumentData> instruments) {
        Map<String, KucoinContractItem> byContracts = cache.contracts();
        return mapFundingByCanonical(instruments, byContracts,
                (inst, c) -> "Open".equalsIgnoreCase(c.status()) ? funding(inst, c.fundingFeeRate(), c.nextFundingRateDateTime()) : null)
                .stream().filter(Objects::nonNull).toList();
    }

    @Override
    public ExchangeType getExchangeType() {
        return ExchangeType.KUCOIN;
    }

    @Override
    public Boolean isEnabled() {
        return true;
    }
}
