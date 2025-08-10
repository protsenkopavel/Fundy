package net.protsenko.fundy.app.exchange.impl.okx;

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
import net.protsenko.fundy.app.props.OkxConfig;
import net.protsenko.fundy.app.utils.HttpExecutor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.stream.Collectors;

import static net.protsenko.fundy.app.utils.ExchangeUtils.toBigDecimal;
import static net.protsenko.fundy.app.utils.ExchangeUtils.toLong;

@Slf4j
@Component
@RequiredArgsConstructor
public class OkxExchangeClient implements ExchangeClient {

    private final ExecutorService executorService = Executors.newFixedThreadPool(10);
    private final HttpExecutor httpExecutor;
    private final OkxConfig config;

    @Override
    public List<InstrumentData> getInstruments() {
        String url = config.getBaseUrl() + "/api/v5/public/instruments?instType=SWAP";
        OkxResponse<OkxInstrumentItem> response = httpExecutor.get(url, config.getTimeout(), new TypeReference<>() {
        });

        if (response == null || !"0".equals(response.code()) || response.data() == null) {
            throw new ExchangeException("OKX instruments error: " + (response != null ? response.msg() : "null"));
        }

        return response.data().stream()
                .filter(instrument -> "SWAP".equalsIgnoreCase(instrument.instType()) && "live".equalsIgnoreCase(instrument.state()))
                .map(this::toInstrument)
                .toList();
    }

    @Override
    public TickerData getTicker(InstrumentData instrument) {
        String symbol = ensureSymbol(instrument);
        String url = config.getBaseUrl() + "/api/v5/market/ticker?instId=" + symbol;
        OkxResponse<OkxTickerItem> response = httpExecutor.get(url, config.getTimeout(), new TypeReference<>() {
        });

        if (response == null || !"0".equals(response.code()) || response.data() == null || response.data().isEmpty()) {
            throw new ExchangeException("OKX ticker error: " + (response != null ? response.msg() : "null"));
        }

        return toTicker(instrument, response.data().getFirst());
    }

    @Override
    public List<TickerData> getTickers(List<InstrumentData> instruments) {
        String url = config.getBaseUrl() + "/api/v5/market/tickers?instType=SWAP";
        OkxResponse<OkxTickerItem> response = httpExecutor.get(url, config.getTimeout(), new TypeReference<>() {
        });

        if (response == null || !"0".equals(response.code()) || response.data() == null) {
            throw new ExchangeException("OKX all-tickers error: " + (response != null ? response.msg() : "null"));
        }

        Map<String, OkxTickerItem> bySymbol = response.data().stream().collect(Collectors.toMap(OkxTickerItem::instId, Function.identity(), (a, b) -> a));

        return instruments.stream()
                .map(instrument -> toTicker(instrument, bySymbol.get(ensureSymbol(instrument))))
                .toList();
    }

    @Override
    public FundingRateData getFundingRate(InstrumentData instrument) {
        String symbol = ensureSymbol(instrument);
        String url = config.getBaseUrl() + "/api/v5/public/funding-rate?instId=" + symbol;
        OkxResponse<OkxFundingItem> response = httpExecutor.get(url, config.getTimeout(), new TypeReference<>() {
        });

        if (response == null || !"0".equals(response.code()) || response.data() == null || response.data().isEmpty()) {
            throw new ExchangeException("OKX funding error: " + (response != null ? response.msg() : "null"));
        }

        return toFunding(instrument, toBigDecimal(response.data().getFirst().fundingRate()), toLong(response.data().getFirst().nextFundingTime()));
    }

    @Override
    public List<FundingRateData> getFundingRates(List<InstrumentData> instruments) {
        List<CompletableFuture<FundingRateData>> futures = instruments.stream()
                .map(instrument -> CompletableFuture.supplyAsync(() -> {
                    try {
                        return getFundingRate(instrument);
                    } catch (Exception e) {
                        throw new ExchangeException("Some problems with OKX response: {}", e);
                    }
                }, executorService))
                .toList();

        return futures.stream()
                .map(CompletableFuture::join)
                .filter(Objects::nonNull)
                .toList();
    }

    @Override
    public ExchangeType getExchangeType() {
        return ExchangeType.OKX;
    }

    @Override
    public Boolean isEnabled() {
        return config.isEnabled();
    }

    private String ensureSymbol(InstrumentData instrument) {
        return instrument.nativeSymbol() != null ? instrument.nativeSymbol() : instrument.baseAsset() + "-" + instrument.quoteAsset() + "-SWAP";
    }

    private InstrumentData toInstrument(OkxInstrumentItem instrument) {
        String[] parts = instrument.instId().split("-");
        String base = parts.length > 0 ? parts[0] : "";
        String quote = parts.length > 1 ? parts[1] : "";
        return new InstrumentData(
                base,
                quote,
                InstrumentType.PERPETUAL,
                instrument.instId(),
                getExchangeType()
        );
    }

    private TickerData toTicker(InstrumentData instrument, OkxTickerItem ticker) {
        return new TickerData(
                instrument,
                toBigDecimal(ticker.last()),
                toBigDecimal(ticker.bidPx()),
                toBigDecimal(ticker.askPx()),
                toBigDecimal(ticker.high24h()),
                toBigDecimal(ticker.low24h()),
                toBigDecimal(ticker.vol24h())
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
