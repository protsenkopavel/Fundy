package net.protsenko.fundy.app.exchange.impl.bingx;

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
import net.protsenko.fundy.app.props.BingxConfig;
import net.protsenko.fundy.app.utils.HttpExecutor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static net.protsenko.fundy.app.utils.ExchangeUtils.toBigDecimal;

@Slf4j
@Component
@RequiredArgsConstructor
public class BingxExchangeClient implements ExchangeClient {

    private final HttpExecutor httpExecutor;
    private final BingxConfig config;

    @Override
    public List<InstrumentData> getInstruments() {
        String url = config.getBaseUrl() + "/openApi/swap/v2/quote/contracts";
        BingxResponse<List<BingxContractItem>> response = httpExecutor.get(url, config.getTimeout(), new TypeReference<>() {
        });

        if (response == null || response.code() != 0 || response.data() == null) {
            throw new ExchangeException("BingX contracts error: " + (response != null ? response.msg() : "null"));
        }

        return response.data().stream()
                .filter(contract -> contract.status() == 1)
                .map(this::toInstrument)
                .toList();
    }

    @Override
    public TickerData getTicker(InstrumentData instrument) {
        String url = config.getBaseUrl() + "/openApi/swap/v2/quote/ticker";
        BingxResponse<List<BingxTickerItem>> response = httpExecutor.get(url, config.getTimeout(), new TypeReference<>() {
        });

        if (response == null || response.code() != 0 || response.data() == null) {
            throw new ExchangeException("BingX ticker error: " + (response != null ? response.msg() : "null"));
        }

        String symbol = ensureSymbol(instrument);
        Map<String, BingxTickerItem> bySymbol = response.data().stream().collect(Collectors.toMap(BingxTickerItem::symbol, Function.identity(), (a, b) -> a));

        return toTicker(instrument, bySymbol.get(symbol));
    }

    @Override
    public List<TickerData> getTickers(List<InstrumentData> instruments) {
        String url = config.getBaseUrl() + "/openApi/swap/v2/quote/ticker";
        BingxResponse<List<BingxTickerItem>> response = httpExecutor.get(url, config.getTimeout(), new TypeReference<>() {
        });

        if (response == null || response.code() != 0 || response.data() == null) {
            throw new ExchangeException("BingX tickers error: " + (response != null ? response.msg() : "null"));
        }

        Map<String, BingxTickerItem> bySymbol = response.data().stream().collect(Collectors.toMap(BingxTickerItem::symbol, Function.identity(), (a, b) -> a));

        return instruments.stream()
                .map(instrument -> toTicker(instrument, bySymbol.get(ensureSymbol(instrument))))
                .toList();
    }

    @Override
    public FundingRateData getFundingRate(InstrumentData instrument) {
        String url = config.getBaseUrl() + "/openApi/swap/v2/quote/premiumIndex";
        BingxResponse<List<BingxPremiumIndexItem>> response = httpExecutor.get(url, config.getTimeout(), new TypeReference<>() {
        });

        if (response == null || response.code() != 0 || response.data() == null) {
            throw new ExchangeException("BingX premiumIndex error: " + (response != null ? response.msg() : "null"));
        }

        String symbol = ensureSymbol(instrument);
        Map<String, BingxPremiumIndexItem> bySymbol = response.data().stream().collect(Collectors.toMap(BingxPremiumIndexItem::symbol, Function.identity(), (a, b) -> a));

        return toFunding(instrument, bySymbol.get(symbol));
    }

    @Override
    public List<FundingRateData> getFundingRates(List<InstrumentData> instruments) {
        String url = config.getBaseUrl() + "/openApi/swap/v2/quote/premiumIndex";
        BingxResponse<List<BingxPremiumIndexItem>> response = httpExecutor.get(url, config.getTimeout(), new TypeReference<>() {
        });

        if (response == null || response.code() != 0 || response.data() == null) {
            throw new ExchangeException("BingX premiumIndex error: " + (response != null ? response.msg() : "null"));
        }

        Map<String, InstrumentData> requested = instruments.stream().collect(Collectors.toMap(this::ensureSymbol, Function.identity(), (a, b) -> a));

        return response.data().stream()
                .filter(index -> requested.containsKey(index.symbol()))
                .map(index -> toFunding(requested.get(index.symbol()), index))
                .toList();
    }

    @Override
    public ExchangeType getExchangeType() {
        return ExchangeType.BINGX;
    }

    @Override
    public Boolean isEnabled() {
        return config.isEnabled();
    }

    private String ensureSymbol(InstrumentData instrument) {
        return instrument.nativeSymbol() != null ?
                instrument.nativeSymbol() :
                instrument.baseAsset() + "-" + instrument.quoteAsset();
    }

    private InstrumentData toInstrument(BingxContractItem contract) {
        return new InstrumentData(
                contract.asset(),
                contract.currency(),
                InstrumentType.PERPETUAL,
                contract.symbol(),
                getExchangeType()
        );
    }

    private TickerData toTicker(InstrumentData instrument, BingxTickerItem ticker) {
        return new TickerData(
                instrument,
                toBigDecimal(ticker.lastPrice()),
                toBigDecimal(ticker.bestBid()),
                toBigDecimal(ticker.bestAsk()),
                toBigDecimal(ticker.high24h()),
                toBigDecimal(ticker.low24h()),
                toBigDecimal(ticker.volume24h())
        );
    }

    private FundingRateData toFunding(InstrumentData instrument, BingxPremiumIndexItem index) {
        return new FundingRateData(
                getExchangeType(),
                instrument,
                toBigDecimal(index.lastFundingRate()),
                index.nextFundingTime()
        );
    }
}
