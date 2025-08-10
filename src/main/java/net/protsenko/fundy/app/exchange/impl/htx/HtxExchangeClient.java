package net.protsenko.fundy.app.exchange.impl.htx;

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
import net.protsenko.fundy.app.props.HtxConfig;
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
public class HtxExchangeClient implements ExchangeClient {

    private final HttpExecutor httpExecutor;
    private final HtxConfig config;

    @Override
    public List<InstrumentData> getInstruments() {
        String url = config.getBaseUrl() + "/linear-swap-api/v1/swap_contract_info";
        HtxResp<List<HtxContractItem>> response = httpExecutor.get(url, config.getTimeout(), new TypeReference<>() {
        });

        if (response == null || !"ok".equalsIgnoreCase(response.status()) || response.data() == null) {
            throw new ExchangeException("HTX instruments error: " + (response != null ? response.status() : "null"));
        }

        return response.data().stream()
                .filter(contract -> contract.contractStatus() == 1)
                .map(this::toInstrument)
                .toList();
    }

    @Override
    public TickerData getTicker(InstrumentData instrument) {
        String symbol = ensureSymbol(instrument);
        String url = config.getBaseUrl() + "/linear-swap-ex/market/detail?contract_code=" + symbol;
        HtxDetailResp response = httpExecutor.get(url, config.getTimeout(), HtxDetailResp.class);

        if (response == null || !"ok".equalsIgnoreCase(response.status()) || response.tick() == null) {
            throw new ExchangeException("HTX ticker error: " + (response != null ? response.status() : "null response"));
        }

        return toTicker(instrument, response.tick());
    }

    @Override
    public List<TickerData> getTickers(List<InstrumentData> instruments) {
        String url = config.getBaseUrl() + "/linear-swap-ex/market/detail/batch_merged";
        HtxBatchResp response = httpExecutor.get(url, config.getTimeout(), HtxBatchResp.class);

        if (response == null || !"ok".equalsIgnoreCase(response.status()) || response.ticks() == null) {
            throw new ExchangeException("HTX batch tickers error: " + (response != null ? response.status() : "null"));
        }

        Map<String, HtxBatchResp.Tick> bySymbol = response.ticks().stream().collect(Collectors.toMap(HtxBatchResp.Tick::contractCode, Function.identity(), (a, b) -> a));

        return instruments.stream()
                .map(instrument -> toTicker(instrument, bySymbol.get(ensureSymbol(instrument))))
                .toList();
    }

    @Override
    public FundingRateData getFundingRate(InstrumentData instrument) {
        String symbol = ensureSymbol(instrument);
        String url = config.getBaseUrl() + "/linear-swap-api/v1/swap_funding_rate?contract_code=" + symbol;
        HtxResp<HtxFundingSingle> response = httpExecutor.get(url, config.getTimeout(), new TypeReference<>() {
        });

        if (response == null || !"ok".equalsIgnoreCase(response.status()) || response.data() == null) {
            throw new ExchangeException("HTX funding error for " + symbol + ": " + (response != null ? response.status() : "null"));
        }

        return toFunding(instrument, toBigDecimal(response.data().fundingRate()), toLong(response.data().fundingTime()));
    }

    @Override
    public List<FundingRateData> getFundingRates(List<InstrumentData> instruments) {
        String url = config.getBaseUrl() + "/linear-swap-api/v1/swap_batch_funding_rate";
        HtxResp<List<HtxFundingItem>> response = httpExecutor.get(url, config.getTimeout(), new TypeReference<>() {
        });

        if (response == null || !"ok".equalsIgnoreCase(response.status()) || response.data() == null) {
            throw new ExchangeException("HTX batch funding error: " + (response != null ? response.status() : "null"));
        }

        Map<String, InstrumentData> requested = instruments.stream().collect(Collectors.toMap(this::ensureSymbol, Function.identity(), (a, b) -> a));

        return response.data().stream()
                .filter(funding -> requested.containsKey(funding.contractCode()))
                .map(funding -> toFunding(requested.get(funding.contractCode()), toBigDecimal(funding.fundingRate()), toLong(funding.fundingTime())))
                .toList();

    }

    @Override
    public ExchangeType getExchangeType() {
        return ExchangeType.HTX;
    }

    @Override
    public Boolean isEnabled() {
        return config.isEnabled();
    }

    private String ensureSymbol(InstrumentData instrument) {
        return instrument.nativeSymbol() != null ?
                instrument.nativeSymbol() :
                instrument.baseAsset().toUpperCase() + "-" + instrument.quoteAsset().toUpperCase();
    }

    private InstrumentData toInstrument(HtxContractItem contract) {
        String[] parts = contract.contractCode().split("-");
        String base = parts.length > 0 ? parts[0] : contract.symbol();
        String quote = parts.length > 1 ? parts[1] : contract.tradePartition();
        return new InstrumentData(
                base,
                quote,
                InstrumentType.PERPETUAL,
                contract.contractCode(),
                getExchangeType()
        );
    }

    private TickerData toTicker(InstrumentData instrument, HtxDetailResp.Tick ticker) {
        return new TickerData(
                instrument,
                toBigDecimal(String.valueOf(ticker.close())),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                toBigDecimal(String.valueOf(ticker.high())),
                toBigDecimal(String.valueOf(ticker.low())),
                toBigDecimal(String.valueOf(ticker.vol()))
        );
    }

    private TickerData toTicker(InstrumentData instrument, HtxBatchResp.Tick ticker) {
        return new TickerData(
                instrument,
                toBigDecimal(String.valueOf(ticker.close())),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                toBigDecimal(String.valueOf(ticker.high())),
                toBigDecimal(String.valueOf(ticker.low())),
                toBigDecimal(String.valueOf(ticker.vol()))
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
