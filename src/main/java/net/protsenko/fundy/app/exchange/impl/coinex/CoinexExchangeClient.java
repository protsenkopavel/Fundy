package net.protsenko.fundy.app.exchange.impl.coinex;

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
import net.protsenko.fundy.app.props.CoinexConfig;
import net.protsenko.fundy.app.utils.HttpExecutor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static net.protsenko.fundy.app.utils.ExchangeUtils.toBigDecimal;

@Slf4j
@Component
@RequiredArgsConstructor
public class CoinexExchangeClient implements ExchangeClient {

    private final HttpExecutor httpExecutor;
    private final CoinexConfig config;

    @Override
    public List<InstrumentData> getInstruments() {
        String url = config.getBaseUrl() + "/perpetual/v1/market/list";
        CoinexResponse<List<CoinexContractItem>> response = httpExecutor.get(url, config.getTimeout(), new TypeReference<>() {
        });

        if (response == null || response.code() != 0 || response.data() == null) {
            throw new ExchangeException("CoinEx instruments error: " + (response != null ? response.message() : "null"));
        }

        return response.data().stream()
                .filter(CoinexContractItem::available)
                .filter(i -> i.type() == 1)
                .map(this::toInstrument)
                .toList();
    }

    @Override
    public TickerData getTicker(InstrumentData instrument) {
        String symbol = ensureSymbol(instrument);
        String url = config.getBaseUrl() + "/perpetual/v1/market/ticker?market=" + symbol;
        CoinexResponse<CoinexTickerSingleData> response = httpExecutor.get(url, config.getTimeout(), new TypeReference<>() {
        });

        if (response == null || response.code() != 0 || response.data() == null || response.data().ticker() == null) {
            throw new ExchangeException("CoinEx ticker error: " + (response != null ? response.message() : "null"));
        }

        return toTicker(instrument, response.data().ticker());
    }

    @Override
    public List<TickerData> getTickers(List<InstrumentData> instruments) {
        String url = config.getBaseUrl() + "/perpetual/v1/market/ticker/all";
        CoinexResponse<CoinexTickerAllData> response = httpExecutor.get(url, config.getTimeout(), new TypeReference<>() {
        });

        if (response == null || response.code() != 0 || response.data() == null || response.data().ticker() == null) {
            throw new ExchangeException("CoinEx ticker/all error: " + (response != null ? response.message() : "null"));
        }

        return instruments.stream()
                .map(inst -> toTicker(inst, response.data().ticker().get(ensureSymbol(inst))))
                .toList();
    }

    @Override
    public FundingRateData getFundingRate(InstrumentData instrument) {
        String url = config.getBaseUrl() + "/perpetual/v1/market/ticker/all";
        CoinexResponse<CoinexTickerAllData> response = httpExecutor.get(url, config.getTimeout(), new TypeReference<>() {
        });

        if (response == null || response.code() != 0 || response.data() == null || response.data().ticker() == null) {
            throw new ExchangeException("CoinEx ticker/all error: " + (response != null ? response.message() : "null"));
        }

        String symbol = ensureSymbol(instrument);

        return toFunding(instrument, toBigDecimal(response.data().ticker().get(symbol).fundingRateLast()), calcNextFundingMs(response.data().ticker().get(symbol).fundingTime()));
    }

    @Override
    public List<FundingRateData> getFundingRates(List<InstrumentData> instruments) {
        String url = config.getBaseUrl() + "/perpetual/v1/market/ticker/all";
        CoinexResponse<CoinexTickerAllData> response = httpExecutor.get(url, config.getTimeout(), new TypeReference<>() {
        });

        if (response == null || response.code() != 0 || response.data() == null || response.data().ticker() == null) {
            throw new ExchangeException("CoinEx ticker/all error: " + (response != null ? response.message() : "null"));
        }

        Map<String, InstrumentData> requested = instruments.stream().collect(Collectors.toMap(this::ensureSymbol, Function.identity(), (a, b) -> a));

        return response.data().ticker().entrySet().stream()
                .filter(entryTicker -> requested.containsKey(entryTicker.getKey()))
                .map(entryTicker -> toFunding(requested.get(entryTicker.getKey()), toBigDecimal(entryTicker.getValue().fundingRateLast()), calcNextFundingMs(entryTicker.getValue().fundingTime())))
                .toList();
    }

    @Override
    public ExchangeType getExchangeType() {
        return ExchangeType.COINEX;
    }

    @Override
    public Boolean isEnabled() {
        return config.isEnabled();
    }

    private String ensureSymbol(InstrumentData instrument) {
        return instrument.nativeSymbol() != null ?
                instrument.nativeSymbol() :
                instrument.baseAsset().toUpperCase() + instrument.quoteAsset().toUpperCase();
    }

    private InstrumentData toInstrument(CoinexContractItem contract) {
        return new InstrumentData(
                contract.stock(),
                contract.money(),
                InstrumentType.PERPETUAL,
                contract.name(),
                getExchangeType()
        );
    }

    private TickerData toTicker(InstrumentData instrument, CoinexTickerItem ticker) {
        return new TickerData(
                instrument,
                toBigDecimal(ticker.last()),
                toBigDecimal(ticker.buy()),
                toBigDecimal(ticker.sell()),
                toBigDecimal(ticker.high()),
                toBigDecimal(ticker.low()),
                toBigDecimal(ticker.vol())
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

    private long calcNextFundingMs(long fundingTimeField) {
        long now = System.currentTimeMillis();
        if (fundingTimeField <= 0) return now;

        long millis = fundingTimeField < 3600
                ? fundingTimeField * 60_000L
                : fundingTimeField * 1000L;

        return now + millis;
    }
}