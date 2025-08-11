package net.protsenko.fundy.app.exchange.impl.gateio;


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
import net.protsenko.fundy.app.props.GateioConfig;
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
public class GateioExchangeClient implements ExchangeClient {

    private final HttpExecutor httpExecutor;
    private final GateioConfig config;

    @Override
    public List<InstrumentData> getInstruments() {
        String url = config.getBaseUrl() + "/api/v4/futures/" + config.getSettle() + "/contracts";
        List<GateioContractItem> response = httpExecutor.get(url, config.getTimeout(), new TypeReference<>() {
        });

        if (response == null) {
            throw new ExchangeException("GateIO contracts: null response");
        }

        return response.stream()
                .filter(contract -> "trading".equalsIgnoreCase(contract.status()))
                .map(this::toInstrument)
                .toList();
    }

    @Override
    public TickerData getTicker(InstrumentData instrument) {
        String symbol = ensureSymbol(instrument);
        String url = config.getBaseUrl() + "/api/v4/futures/" + config.getSettle() + "/tickers";
        List<GateioTickerItem> response = httpExecutor.get(url, config.getTimeout(), new TypeReference<>() {
        });

        if (response == null) {
            throw new ExchangeException("GateIO tickers: null response");
        }

        GateioTickerItem ticker = response.stream()
                .filter(tickerItem -> symbol.equalsIgnoreCase(tickerItem.contract()))
                .findFirst()
                .orElseThrow(() -> new ExchangeException("Ticker not found: " + symbol));


        return toTicker(instrument, ticker);
    }

    @Override
    public List<TickerData> getTickers(List<InstrumentData> instruments) {
        String url = "https://api.gateio.ws/api/v4/futures/" + config.getSettle() + "/tickers";
        List<GateioTickerItem> response = httpExecutor.get(url, config.getTimeout(), new TypeReference<>() {
        });

        if (response == null) {
            throw new ExchangeException("GateIO tickers: null response");
        }

        Map<String, GateioTickerItem> bySymbol = response.stream().collect(Collectors.toMap(GateioTickerItem::contract, Function.identity(), (a, b) -> a));

        return instruments.stream()
                .map(instrument -> toTicker(instrument, bySymbol.get(ensureSymbol(instrument))))
                .toList();
    }

    @Override
    public FundingRateData getFundingRate(InstrumentData instrument) {
        String symbol = ensureSymbol(instrument);
        String url = config.getBaseUrl() + "/api/v4/futures/" + config.getSettle() + "/contracts";
        List<GateioContractItem> response = httpExecutor.get(url, config.getTimeout(), new TypeReference<>() {
        });

        if (response == null) {
            throw new ExchangeException("GateIO contracts: null response");
        }

        GateioContractItem meta = response.stream()
                .filter(contract -> symbol.equalsIgnoreCase(contract.name()))
                .findFirst()
                .orElseThrow(() -> new ExchangeException("No contract meta for " + symbol));

        return toFunding(instrument, toBigDecimal(meta.fundingRate()), meta.fundingNextApply() * 1000L);
    }

    @Override
    public List<FundingRateData> getFundingRates(List<InstrumentData> instruments) {
        String url = config.getBaseUrl() + "/api/v4/futures/" + config.getSettle() + "/contracts";
        List<GateioContractItem> response = httpExecutor.get(url, config.getTimeout(), new TypeReference<>() {
        });

        if (response == null) {
            throw new ExchangeException("GateIO contracts: null response");
        }

        Map<String, InstrumentData> requested = instruments.stream().collect(Collectors.toMap(this::ensureSymbol, Function.identity(), (a, b) -> a));

        return response.stream()
                .filter(contract -> "trading".equalsIgnoreCase(contract.status()))
                .filter(contract -> requested.containsKey(contract.name()))
                .map(contract -> toFunding(requested.get(contract.name()), toBigDecimal(contract.fundingRate()), contract.fundingNextApply() * 1000L))
                .toList();
    }

    @Override
    public ExchangeType getExchangeType() {
        return ExchangeType.GATEIO;
    }

    @Override
    public Boolean isEnabled() {
        return config.isEnabled();
    }

    private String ensureSymbol(InstrumentData instrument) {
        return instrument.nativeSymbol() != null ?
                instrument.nativeSymbol() :
                instrument.baseAsset() + "_" + instrument.quoteAsset();
    }

    private InstrumentData toInstrument(GateioContractItem contract) {
        String nativeSymbol = contract.name();
        String[] parts = nativeSymbol.split("_");
        String base = parts.length > 0 ? parts[0] : "";
        String quote = parts.length > 1 ? parts[1] : config.getSettle().toUpperCase();
        return new InstrumentData(
                base,
                quote,
                InstrumentType.PERPETUAL,
                nativeSymbol,
                getExchangeType()
        );
    }

    private TickerData toTicker(InstrumentData instrument, GateioTickerItem ticker) {
        return new TickerData(
                instrument,
                toBigDecimal(ticker.last()),
                toBigDecimal(ticker.highestBid()),
                toBigDecimal(ticker.lowestAsk()),
                toBigDecimal(ticker.high24h()),
                toBigDecimal(ticker.low24h()),
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
