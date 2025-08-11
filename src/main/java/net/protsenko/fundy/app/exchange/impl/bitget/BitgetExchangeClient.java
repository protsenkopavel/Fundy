package net.protsenko.fundy.app.exchange.impl.bitget;

import com.fasterxml.jackson.core.type.TypeReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.protsenko.fundy.app.dto.InstrumentType;
import net.protsenko.fundy.app.dto.rs.FundingRateData;
import net.protsenko.fundy.app.dto.rs.InstrumentData;
import net.protsenko.fundy.app.dto.rs.TickerData;
import net.protsenko.fundy.app.exception.ExchangeException;
import net.protsenko.fundy.app.exchange.ExchangeClient;
import net.protsenko.fundy.app.exchange.ExchangeType;
import net.protsenko.fundy.app.props.BitgetConfig;
import net.protsenko.fundy.app.utils.HttpExecutor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static net.protsenko.fundy.app.utils.ExchangeUtils.toBigDecimal;

@Slf4j
@Component
@RequiredArgsConstructor
public class BitgetExchangeClient implements ExchangeClient {

    private final HttpExecutor httpExecutor;
    private final BitgetConfig config;

    @Override
    public List<InstrumentData> getInstruments() {
        String url = config.getBaseUrl() + "/api/mix/v1/market/contracts?productType=" + config.getProductType();
        BitgetResponse<List<BitgetContractItem>> response = httpExecutor.get(url, config.getTimeout(), new TypeReference<>() {
        });

        if (response == null || !"00000".equals(response.code()) || response.data() == null) {
            throw new ExchangeException("Bitget instruments error: " + (response != null ? response.msg() : "null response"));
        }

        return response.data().stream()
                .filter(contract -> "normal".equalsIgnoreCase(contract.symbolStatus()))
                .map(this::toInstrument)
                .toList();
    }

    @Override
    public TickerData getTicker(InstrumentData instrument) {
        String symbol = ensureSymbol(instrument);
        String url = config.getBaseUrl() + "/api/mix/v1/market/ticker?symbol=" + symbol;
        BitgetResponse<BitgetTickerItem> response = httpExecutor.get(url, config.getTimeout(), new TypeReference<>() {
        });

        if (response == null || !"00000".equals(response.code()) || response.data() == null) {
            throw new ExchangeException("Bitget ticker error: " + (response != null ? response.msg() : "null response"));
        }

        return toTicker(instrument, response.data());
    }

    @Override
    public List<TickerData> getTickers(List<InstrumentData> instruments) {
        String url = config.getBaseUrl() + "/api/mix/v1/market/tickers?productType=" + config.getProductType();
        BitgetResponse<List<BitgetTickerItem>> response = httpExecutor.get(url, config.getTimeout(), new TypeReference<>() {
        });

        if (response == null || !"00000".equals(response.code()) || response.data() == null) {
            throw new ExchangeException("Bitget tickers error: " + (response != null ? response.msg() : "null response"));
        }

        Map<String, BitgetTickerItem> bySymbol = response.data().stream().collect(Collectors.toMap(BitgetTickerItem::symbol, Function.identity(), (a, b) -> a));

        return instruments.stream()
                .map(instrument -> toTicker(instrument, bySymbol.get(ensureSymbol(instrument))))
                .toList();
    }

    @Override
    public FundingRateData getFundingRate(InstrumentData instrument) {
        String symbol = ensureSymbol(instrument);
        String url = config.getBaseUrl() + "/api/mix/v1/market/current-fundRate?symbol=" + symbol;
        BitgetResponse<BitgetFundingItem> response = httpExecutor.get(url, config.getTimeout(), new TypeReference<>() {
        });

        if (response == null || !"00000".equals(response.code()) || response.data() == null) {
            throw new ExchangeException("Bitget funding error: " + (response != null ? response.msg() : "null response"));
        }

        return toFunding(instrument, toBigDecimal(response.data().fundingRate()), nextFundingTimeGlobal());
    }

    @Override
    public List<FundingRateData> getFundingRates(List<InstrumentData> instruments) {
        String url = config.getBaseUrl() + "/api/mix/v1/market/tickers?productType=" + config.getProductType();
        BitgetResponse<List<BitgetTickerItem>> response = httpExecutor.get(url, config.getTimeout(), new TypeReference<>() {
        });

        if (response == null || !"00000".equals(response.code()) || response.data() == null) {
            throw new ExchangeException("Bitget tickers error: " + (response != null ? response.msg() : "null response"));
        }

        Map<String, InstrumentData> requested = instruments.stream().collect(Collectors.toMap(this::ensureSymbol, Function.identity(), (a, b) -> a));

        return response.data().stream()
                .filter(ticker -> requested.containsKey(ticker.symbol()))
                .map(ticker -> toFunding(requested.get(ticker.symbol()), toBigDecimal(ticker.fundingRate()), nextFundingTimeGlobal()))
                .toList();
    }

    @Override
    public ExchangeType getExchangeType() {
        return ExchangeType.BITGET;
    }

    @Override
    public Boolean isEnabled() {
        return config.isEnabled();
    }

    private String ensureSymbol(InstrumentData instrument) {
        return instrument.nativeSymbol() != null ?
                instrument.nativeSymbol() :
                instrument.baseAsset() + instrument.quoteAsset() + "_" + config.getProductType().toUpperCase();
    }

    private InstrumentData toInstrument(BitgetContractItem contract) {
        return new InstrumentData(
                contract.baseCoin(),
                contract.quoteCoin(),
                InstrumentType.PERPETUAL,
                contract.symbol(),
                getExchangeType()
        );
    }

    private TickerData toTicker(InstrumentData instrument, BitgetTickerItem ticker) {
        return new TickerData(
                instrument,
                toBigDecimal(ticker.last()),
                toBigDecimal(ticker.bestBid()),
                toBigDecimal(ticker.bestAsk()),
                toBigDecimal(ticker.high24h()),
                toBigDecimal(ticker.low24h()),
                toBigDecimal(ticker.baseVolume())
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

    private long nextFundingTimeGlobal() {
        return ((System.currentTimeMillis() / Duration.ofHours(8).toMillis()) + 1) * Duration.ofHours(8).toMillis();
    }
}
