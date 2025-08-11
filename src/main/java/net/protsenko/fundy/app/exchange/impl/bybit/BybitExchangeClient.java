package net.protsenko.fundy.app.exchange.impl.bybit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.protsenko.fundy.app.dto.InstrumentType;
import net.protsenko.fundy.app.dto.rs.FundingRateData;
import net.protsenko.fundy.app.dto.rs.InstrumentData;
import net.protsenko.fundy.app.dto.rs.TickerData;
import net.protsenko.fundy.app.exception.ExchangeException;
import net.protsenko.fundy.app.exchange.ExchangeClient;
import net.protsenko.fundy.app.exchange.ExchangeType;
import net.protsenko.fundy.app.props.BybitConfig;
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
public class BybitExchangeClient implements ExchangeClient {

    private final HttpExecutor httpExecutor;
    private final BybitConfig config;

    @Override
    public List<InstrumentData> getInstruments() {
        String url = config.getBaseUrl() + "/v5/market/instruments-info?category=linear";
        BybitInstrumentsResponse response = httpExecutor.get(url, config.getTimeout(), BybitInstrumentsResponse.class);

        if (response == null || response.retCode() != 0 || response.result() == null) {
            throw new ExchangeException("Bybit instruments error: " + (response != null ? response.retMsg() : "null response"));
        }

        return response.result().list().stream()
                .filter(instrument -> "Trading".equalsIgnoreCase(instrument.status()))
                .map(this::toInstrument)
                .toList();
    }

    @Override
    public TickerData getTicker(InstrumentData instrument) {
        String symbol = ensureSymbol(instrument);
        String url = config.getBaseUrl() + "/v5/market/tickers?category=linear&symbol=" + symbol;
        BybitTickerResponse response = httpExecutor.get(url, config.getTimeout(), BybitTickerResponse.class);

        if (response == null || response.retCode() != 0 || response.result() == null || response.result().list().isEmpty()) {
            throw new ExchangeException("Bybit ticker error for " + symbol + ": " + (response != null ? response.retMsg() : "null response"));
        }

        return toTicker(instrument, response.result().list().getFirst());
    }

    @Override
    public List<TickerData> getTickers(List<InstrumentData> instruments) {
        String url = config.getBaseUrl() + "/v5/market/tickers?category=linear";
        BybitTickerResponse response = httpExecutor.get(url, config.getTimeout(), BybitTickerResponse.class);

        if (response == null || response.retCode() != 0 || response.result() == null)
            throw new ExchangeException("Bybit tickers error: " + (response != null ? response.retMsg() : "null"));

        Map<String, BybitTickerItem> bySymbol = response.result().list().stream().collect(Collectors.toMap(BybitTickerItem::symbol, Function.identity(), (a, b) -> a));

        return instruments.stream()
                .map(instrument -> toTicker(instrument, bySymbol.get(instrument.nativeSymbol())))
                .toList();
    }

    @Override
    public FundingRateData getFundingRate(InstrumentData instrument) {
        String symbol = ensureSymbol(instrument);
        String url = config.getBaseUrl() + "/v5/market/tickers?category=linear&symbol=" + symbol;
        BybitTickerResponse response = httpExecutor.get(url, config.getTimeout(), BybitTickerResponse.class);

        if (response == null || response.retCode() != 0 || response.result() == null || response.result().list().isEmpty()) {
            throw new ExchangeException("Bybit funding error for " + symbol + ": " + (response != null ? response.retMsg() : "null response"));
        }

        return toFunding(instrument, toBigDecimal(response.result().list().getFirst().fundingRate()), toLong(response.result().list().getFirst().nextFundingTime()));
    }

    @Override
    public List<FundingRateData> getFundingRates(List<InstrumentData> instruments) {
        String url = config.getBaseUrl() + "/v5/market/tickers?category=linear";
        BybitTickerResponse resp = httpExecutor.get(url, config.getTimeout(), BybitTickerResponse.class);

        if (resp == null || resp.retCode() != 0 || resp.result() == null) {
            throw new ExchangeException("Bybit tickers error: " + (resp != null ? resp.retMsg() : "null response"));
        }

        Map<String, InstrumentData> requested = instruments.stream().collect(Collectors.toMap(this::ensureSymbol, Function.identity(), (a, b) -> a));

        return resp.result().list().stream()
                .filter(ticker -> requested.containsKey(ticker.symbol()))
                .map(ticker -> toFunding(requested.get(ticker.symbol()), toBigDecimal(ticker.fundingRate()), toLong(ticker.nextFundingTime())))
                .toList();
    }

    @Override
    public ExchangeType getExchangeType() {
        return ExchangeType.BYBIT;
    }

    @Override
    public Boolean isEnabled() {
        return config.isEnabled();
    }

    private String ensureSymbol(InstrumentData instrument) {
        return instrument.nativeSymbol() != null ?
                instrument.nativeSymbol() :
                instrument.baseAsset() + instrument.quoteAsset();
    }

    private InstrumentData toInstrument(BybitInstrumentItem instrument) {
        return new InstrumentData(
                instrument.baseCoin(),
                instrument.quoteCoin(),
                InstrumentType.PERPETUAL,
                instrument.symbol(),
                getExchangeType()
        );
    }

    private TickerData toTicker(InstrumentData instrument, BybitTickerItem ticker) {
        return new TickerData(
                instrument,
                toBigDecimal(ticker.lastPrice()),
                toBigDecimal(ticker.bid1Price()),
                toBigDecimal(ticker.ask1Price()),
                toBigDecimal(ticker.highPrice24h()),
                toBigDecimal(ticker.volume24h()),
                toBigDecimal(ticker.volume24h())
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
