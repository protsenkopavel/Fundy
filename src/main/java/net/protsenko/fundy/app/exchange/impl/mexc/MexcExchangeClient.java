package net.protsenko.fundy.app.exchange.impl.mexc;

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
import net.protsenko.fundy.app.props.MexcConfig;
import net.protsenko.fundy.app.utils.HttpExecutor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

import static net.protsenko.fundy.app.utils.ExchangeUtils.toLong;

@Slf4j
@Component
@RequiredArgsConstructor
public class MexcExchangeClient implements ExchangeClient, ExchangeMappingSupport {

    private final HttpExecutor httpExecutor;
    private final MexcConfig config;

    @Override
    public List<InstrumentData> getInstruments() {
        String url = config.getBaseUrl() + "/api/v1/contract/detail";
        MexcInstrumentsResponse resp = httpExecutor.get(url, config.getTimeout(), MexcInstrumentsResponse.class);

        require(resp != null && resp.code() == 0 && resp.data() != null,
                () -> "MEXC instruments error: " + (resp != null ? resp.msg() : "null response"));

        return resp.data().stream()
                .filter(i -> i.state() == 0)
                .map(i -> instrument(
                        i.baseCoin(),
                        i.quoteCoin(),
                        InstrumentType.PERPETUAL,
                        i.symbol()
                ))
                .toList();
    }

    @Override
    public TickerData getTicker(InstrumentData instrument) {
        String url = config.getBaseUrl() + "/api/v1/contract/ticker";
        MexcTickerListWrapper resp = httpExecutor.get(url, config.getTimeout(), MexcTickerListWrapper.class);

        require(resp != null && resp.code() == 0 && resp.data() != null,
                () -> "MEXC tickers error: " + (resp != null ? resp.msg() : "null response"));

        Map<String, MexcTickerItem> byCanonical = indexByCanonical(resp.data(), MexcTickerItem::symbol);

        return mapTickersByCanonical(
                List.of(instrument),
                byCanonical,
                (inst, t) -> ticker(
                        inst,
                        t.lastPrice(),
                        t.bid1Price(),
                        t.ask1Price(),
                        t.high24Price(),
                        t.low24Price(),
                        t.volume24()
                )
        ).stream().findFirst().orElseThrow(() ->
                new ExchangeException("[" + getExchangeType() + "] ticker not found for "
                        + instrument.baseAsset() + "/" + instrument.quoteAsset()));
    }

    @Override
    public List<TickerData> getTickers(List<InstrumentData> instruments) {
        String url = config.getBaseUrl() + "/api/v1/contract/ticker";
        MexcTickerListWrapper resp = httpExecutor.get(url, config.getTimeout(), MexcTickerListWrapper.class);

        require(resp != null && resp.code() == 0 && resp.data() != null,
                () -> "MEXC tickers error: " + (resp != null ? resp.msg() : "null response"));

        Map<String, MexcTickerItem> byCanonical = indexByCanonical(resp.data(), MexcTickerItem::symbol);

        return mapTickersByCanonical(
                instruments,
                byCanonical,
                (inst, t) -> ticker(
                        inst,
                        t.lastPrice(),
                        t.bid1Price(),
                        t.ask1Price(),
                        t.high24Price(),
                        t.low24Price(),
                        t.volume24()
                )
        );
    }

    @Override
    public FundingRateData getFundingRate(InstrumentData instrument) {
        String url = config.getBaseUrl() + "/api/v1/contract/funding_rate";
        MexcFundingListResponse resp = httpExecutor.get(url, config.getTimeout(), MexcFundingListResponse.class);

        require(resp != null && resp.code() == 0 && resp.data() != null && !resp.data().isEmpty(),
                () -> "MEXC funding error: " + (resp != null ? resp.msg() : "null response"));

        Map<String, MexcFundingItem> byCanonical = indexByCanonical(resp.data(), MexcFundingItem::symbol);

        return mapFundingByCanonical(
                List.of(instrument),
                byCanonical,
                (inst, f) -> funding(inst, f.fundingRate(), toLong(f.fundingTime()))
        ).stream().findFirst().orElseThrow(() ->
                new ExchangeException("[" + getExchangeType() + "] funding not found for "
                        + instrument.baseAsset() + "/" + instrument.quoteAsset()));
    }

    @Override
    public List<FundingRateData> getFundingRates(List<InstrumentData> instruments) {
        String url = config.getBaseUrl() + "/api/v1/contract/funding_rate";
        MexcFundingListResponse resp = httpExecutor.get(url, config.getTimeout(), MexcFundingListResponse.class);

        require(resp != null && resp.code() == 0 && resp.data() != null && !resp.data().isEmpty(),
                () -> "MEXC funding error: " + (resp != null ? resp.msg() : "null response"));

        Map<String, MexcFundingItem> byCanonical = indexByCanonical(resp.data(), MexcFundingItem::symbol);

        return mapFundingByCanonical(
                instruments,
                byCanonical,
                (inst, f) -> funding(inst, f.fundingRate(), toLong(f.fundingTime()))
        );
    }

    @Override
    public ExchangeType getExchangeType() {
        return ExchangeType.MEXC;
    }

    @Override
    public Boolean isEnabled() {
        return config.isEnabled();
    }
}
