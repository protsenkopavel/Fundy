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
import net.protsenko.fundy.app.props.KucoinConfig;
import net.protsenko.fundy.app.utils.HttpExecutor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import static net.protsenko.fundy.app.utils.ExchangeUtils.toBigDecimal;

@Slf4j
@Component
@RequiredArgsConstructor
public class KucoinExchangeClient implements ExchangeClient {

    private final HttpExecutor httpExecutor;
    private final KucoinConfig config;

    @Override
    public List<InstrumentData> getInstruments() {
        String url = config.getBaseUrl() + "/api/v1/contracts/active";
        KucoinContractsResponse response = httpExecutor.get(url, config.getTimeout(), KucoinContractsResponse.class);

        if (response == null || response.data() == null || response.data().isEmpty()) {
            throw new ExchangeException("KuCoin returned empty contracts list");
        }

        return response.data().stream()
                .filter(contract -> "Open".equalsIgnoreCase(contract.status()))
                .map(this::toInstrument)
                .toList();
    }

    @Override
    public TickerData getTicker(InstrumentData instrument) {
        String symbol = ensureSymbol(instrument);
        String tUrl = config.getBaseUrl() + "/api/v1/ticker?symbol=" + symbol;
        KucoinTickerResponse tickerResponse = httpExecutor.get(tUrl, config.getTimeout(), KucoinTickerResponse.class);

        if (tickerResponse == null || !"200000".equals(tickerResponse.code()) || tickerResponse.data() == null) {
            throw new ExchangeException("KuCoin ticker error: " + (tickerResponse != null ? tickerResponse.code() : "null"));
        }

        String cUrl = config.getBaseUrl() + "/api/v1/contracts/" + symbol;
        KucoinContractSingleResponse contractResponse = httpExecutor.get(cUrl, config.getTimeout(), KucoinContractSingleResponse.class);

        if (contractResponse == null || contractResponse.data() == null) {
            throw new ExchangeException("KuCoin contract not found: " + symbol);
        }

        return toTicker(instrument, tickerResponse.data(), contractResponse.data());
    }

    @Override
    public List<TickerData> getTickers(List<InstrumentData> instruments) {
        String tUrl = config.getBaseUrl() + "/api/v1/allTickers";
        KucoinAllTickersResponse tickersResponse = httpExecutor.get(tUrl, config.getTimeout(), KucoinAllTickersResponse.class);

        if (tickersResponse == null || !"200000".equals(tickersResponse.code()) || tickersResponse.data() == null) {
            throw new ExchangeException("KuCoin allTickers error");
        }

        var bySymbol = tickersResponse.data().stream().collect(Collectors.toMap(KucoinTickerData::symbol, Function.identity(), (a, b) -> a));

        String cUrl = config.getBaseUrl() + "/api/v1/contracts/active";
        KucoinContractsResponse contractsResponse = httpExecutor.get(cUrl, config.getTimeout(), KucoinContractsResponse.class);

        if (contractsResponse == null || contractsResponse.data() == null) {
            throw new ExchangeException("KuCoin contracts fetch error");
        }
        var contractMap = contractsResponse.data().stream().collect(Collectors.toMap(KucoinContractItem::symbol, Function.identity(), (a, b) -> a));

        return instruments.stream()
                .map(instrument -> {
                    String sym = ensureSymbol(instrument);
                    return toTicker(instrument, bySymbol.get(sym), contractMap.get(sym));
                })
                .toList();
    }

    @Override
    public FundingRateData getFundingRate(InstrumentData instrument) {
        String symbol = ensureSymbol(instrument);
        String url = config.getBaseUrl() + "/api/v1/contracts/" + symbol;
        KucoinContractSingleResponse response = httpExecutor.get(url, config.getTimeout(), KucoinContractSingleResponse.class);

        if (response == null || response.data() == null) {
            throw new ExchangeException("KuCoin contract not found: " + symbol);
        }

        return toFunding(instrument, toBigDecimal(response.data().fundingFeeRate()), response.data().nextFundingRateDateTime());
    }

    @Override
    public List<FundingRateData> getFundingRates(List<InstrumentData> instruments) {
        String url = config.getBaseUrl() + "/api/v1/contracts/active";
        KucoinContractsResponse response = httpExecutor.get(url, config.getTimeout(), KucoinContractsResponse.class);

        if (response == null || response.data() == null) {
            throw new ExchangeException("Contracts fetch error: null response");
        }

        var requested = instruments.stream().collect(Collectors.toMap(this::ensureSymbol, Function.identity(), (a, b) -> a));

        return response.data().stream()
                .filter(contract -> "Open".equalsIgnoreCase(contract.status()))
                .filter(contract -> requested.containsKey(contract.symbol()))
                .map(contract -> toFunding(requested.get(contract.symbol()), toBigDecimal(contract.fundingFeeRate()), contract.nextFundingRateDateTime()))
                .toList();
    }

    @Override
    public ExchangeType getExchangeType() {
        return ExchangeType.KUCOIN;
    }

    @Override
    public Boolean isEnabled() {
        return config.isEnabled();
    }

    private String ensureSymbol(InstrumentData instrument) {
        return instrument.nativeSymbol() != null
                ? instrument.nativeSymbol()
                : instrument.baseAsset() + instrument.quoteAsset() + "M";
    }

    private InstrumentData toInstrument(KucoinContractItem contract) {
        return new InstrumentData(
                contract.baseCurrency(),
                contract.quoteCurrency(),
                InstrumentType.PERPETUAL,
                contract.symbol(),
                getExchangeType()
        );
    }

    private TickerData toTicker(InstrumentData instrument, KucoinTickerData ticker, KucoinContractItem contract) {
        return new TickerData(
                instrument,
                toBigDecimal(ticker.price()),
                toBigDecimal(ticker.bestBidPrice()),
                toBigDecimal(ticker.bestAskPrice()),
                toBigDecimal(contract.highPrice()),
                toBigDecimal(contract.lowPrice()),
                toBigDecimal(contract.volumeOf24h())
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
