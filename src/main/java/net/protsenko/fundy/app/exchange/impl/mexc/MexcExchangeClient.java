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
import net.protsenko.fundy.app.props.MexcConfig;
import net.protsenko.fundy.app.utils.HttpExecutor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static net.protsenko.fundy.app.utils.ExchangeUtils.toBigDecimal;
import static net.protsenko.fundy.app.utils.ExchangeUtils.toLong;

@Slf4j
@Component
@RequiredArgsConstructor
public class MexcExchangeClient implements ExchangeClient {

    private final HttpExecutor httpExecutor;
    private final MexcConfig config;

    @Override
    public List<InstrumentData> getInstruments() {
        String url = config.getBaseUrl() + "/api/v1/contract/detail";
        MexcInstrumentsResponse response = httpExecutor.get(url, config.getTimeout(), MexcInstrumentsResponse.class);

        if (response == null || response.code() != 0 || response.data() == null) {
            throw new ExchangeException("MEXC instruments error: " + (response != null ? response.msg() : "null response"));
        }

        return response.data().stream()
                .filter(instrument -> instrument.state() == 0)
                .map(this::toInstrument)
                .toList();
    }

    @Override
    public TickerData getTicker(InstrumentData instrument) {
        String symbol = ensureSymbol(instrument);
        String url = config.getBaseUrl() + "/api/v1/contract/ticker?symbol=" + symbol;
        MexcTickerResponse response = httpExecutor.get(url, config.getTimeout(), MexcTickerResponse.class);

        if (response == null || response.code() != 0 || response.data() == null) {
            throw new ExchangeException("MEXC ticker error: " + (response != null ? response.msg() : "null response"));
        }

        return toTicker(instrument, response.data());
    }

    @Override
    public List<TickerData> getTickers(List<InstrumentData> instruments) {
        String url = config.getBaseUrl() + "/api/v1/contract/ticker";
        MexcTickerListWrapper response = httpExecutor.get(url, config.getTimeout(), MexcTickerListWrapper.class);

        if (response == null || response.code() != 0 || response.data() == null) {
            throw new ExchangeException("MEXC tickers error: " + (response != null ? response.msg() : "null"));
        }

        Map<String, MexcTickerItem> bySymbol = response.data().stream().collect(Collectors.toMap(MexcTickerItem::symbol, Function.identity(), (a, b) -> a));

        return instruments.stream()
                .map(inst -> toTicker(inst, bySymbol.get(ensureSymbol(inst))))
                .toList();
    }

    @Override
    public FundingRateData getFundingRate(InstrumentData instrument) {
        String symbol = ensureSymbol(instrument);
        String url = config.getBaseUrl() + "/api/v1/contract/funding_rate?symbol=" + symbol;
        MexcFundingListResponse response = httpExecutor.get(url, config.getTimeout(), MexcFundingListResponse.class);

        if (response == null || response.code() != 0 || response.data() == null || response.data().isEmpty()) {
            throw new ExchangeException("MEXC funding error for " + symbol + ": " + (response != null ? response.msg() : "null response"));
        }

        MexcFundingItem funding = response.data().stream()
                .filter(fundingItem -> symbol.equalsIgnoreCase(fundingItem.symbol()))
                .findFirst()
                .orElse(response.data().getFirst());

        return toFunding(instrument, toBigDecimal(funding.fundingRate()), toLong(funding.fundingTime()));
    }

    @Override
    public List<FundingRateData> getFundingRates(List<InstrumentData> instruments) {
        String url = config.getBaseUrl() + "/api/v1/contract/funding_rate";
        MexcFundingListResponse response = httpExecutor.get(url, config.getTimeout(), MexcFundingListResponse.class);

        if (response == null || response.code() != 0 || response.data() == null || response.data().isEmpty()) {
            throw new ExchangeException("MEXC funding error for " + (response != null ? response.msg() : "null response"));
        }

        Map<String, InstrumentData> requested = instruments.stream()
                .collect(Collectors.toMap(this::ensureSymbol, Function.identity(), (a, b) -> a));

        return response.data().stream()
                .filter(funding -> requested.containsKey(funding.symbol()))
                .map(funding -> toFunding(requested.get(funding.symbol()), toBigDecimal(funding.fundingRate()), toLong(funding.fundingTime())))
                .toList();

    }

    @Override
    public ExchangeType getExchangeType() {
        return ExchangeType.MEXC;
    }

    @Override
    public Boolean isEnabled() {
        return config.isEnabled();
    }

    private String ensureSymbol(InstrumentData instrument) {
        return instrument.nativeSymbol() != null
                ? instrument.nativeSymbol()
                : instrument.baseAsset() + "_" + instrument.quoteAsset();
    }

    private InstrumentData toInstrument(MexcInstrumentItem instrument) {
        return new InstrumentData(
                instrument.baseCoin(),
                instrument.quoteCoin(),
                InstrumentType.PERPETUAL,
                instrument.symbol(),
                getExchangeType()
        );
    }

    private TickerData toTicker(InstrumentData instrument, MexcTickerItem ticker) {
        return new TickerData(
                instrument,
                toBigDecimal(ticker.lastPrice()),
                toBigDecimal(ticker.bid1Price()),
                toBigDecimal(ticker.ask1Price()),
                toBigDecimal(ticker.high24Price()),
                toBigDecimal(ticker.low24Price()),
                toBigDecimal(ticker.volume24())
        );
    }

    private FundingRateData toFunding(InstrumentData instrument, BigDecimal fundingRate, long nextFundingTimeMs) {
        return new FundingRateData(
                getExchangeType(),
                instrument,
                fundingRate,
                nextFundingTimeMs
        );
    }
}
