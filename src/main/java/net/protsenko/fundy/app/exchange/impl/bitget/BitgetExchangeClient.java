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
import net.protsenko.fundy.app.exchange.support.ExchangeMappingSupport;
import net.protsenko.fundy.app.props.BitgetConfig;
import net.protsenko.fundy.app.utils.HttpExecutor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class BitgetExchangeClient implements ExchangeClient, ExchangeMappingSupport {

    private final HttpExecutor httpExecutor;
    private final BitgetConfig config;

    @Override
    public List<InstrumentData> getInstruments() {
        String url = config.getBaseUrl() + "/api/mix/v1/market/contracts?productType=" + config.getProductType();
        BitgetResponse<List<BitgetContractItem>> resp =
                httpExecutor.get(url, config.getTimeout(), new TypeReference<>() {
                });

        require(resp != null && "00000".equals(resp.code()) && resp.data() != null,
                () -> "Bitget instruments error: " + (resp != null ? resp.msg() : "null response"));

        return resp.data().stream()
                .filter(c -> "normal".equalsIgnoreCase(c.symbolStatus()))
                .map(c -> instrument(
                        c.baseCoin(),
                        c.quoteCoin(),
                        InstrumentType.PERPETUAL,
                        c.symbol()
                ))
                .toList();
    }

    @Override
    public TickerData getTicker(InstrumentData instrument) {
        String url = config.getBaseUrl() + "/api/mix/v1/market/tickers?productType=" + config.getProductType();
        BitgetResponse<List<BitgetTickerItem>> resp =
                httpExecutor.get(url, config.getTimeout(), new TypeReference<>() {
                });

        require(resp != null && "00000".equals(resp.code()) && resp.data() != null,
                () -> "Bitget tickers error: " + (resp != null ? resp.msg() : "null response"));

        Map<String, BitgetTickerItem> byCanonical = indexByCanonical(resp.data(), BitgetTickerItem::symbol);

        return mapTickersByCanonical(
                List.of(instrument),
                byCanonical,
                (inst, t) -> ticker(
                        inst,
                        t.last(),
                        t.bestBid(),
                        t.bestAsk(),
                        t.high24h(),
                        t.low24h(),
                        t.baseVolume()
                )
        ).stream().findFirst().orElseThrow(() ->
                new ExchangeException("[" + getExchangeType() + "] ticker not found for "
                        + instrument.baseAsset() + "/" + instrument.quoteAsset()));
    }

    @Override
    public List<TickerData> getTickers(List<InstrumentData> instruments) {
        String url = config.getBaseUrl() + "/api/mix/v1/market/tickers?productType=" + config.getProductType();
        BitgetResponse<List<BitgetTickerItem>> resp =
                httpExecutor.get(url, config.getTimeout(), new TypeReference<>() {
                });

        require(resp != null && "00000".equals(resp.code()) && resp.data() != null,
                () -> "Bitget tickers error: " + (resp != null ? resp.msg() : "null response"));

        Map<String, BitgetTickerItem> byCanonical = indexByCanonical(resp.data(), BitgetTickerItem::symbol);

        return mapTickersByCanonical(
                instruments,
                byCanonical,
                (inst, t) -> ticker(
                        inst,
                        t.last(),
                        t.bestBid(),
                        t.bestAsk(),
                        t.high24h(),
                        t.low24h(),
                        t.baseVolume()
                )
        );
    }

    @Override
    public FundingRateData getFundingRate(InstrumentData instrument) {
        String url = config.getBaseUrl() + "/api/mix/v1/market/tickers?productType=" + config.getProductType();
        BitgetResponse<List<BitgetTickerItem>> resp =
                httpExecutor.get(url, config.getTimeout(), new TypeReference<>() {
                });

        require(resp != null && "00000".equals(resp.code()) && resp.data() != null,
                () -> "Bitget funding error: " + (resp != null ? resp.msg() : "null response"));

        Map<String, BitgetTickerItem> byCanonical = indexByCanonical(resp.data(), BitgetTickerItem::symbol);
        long next = nextFundingAlignedHours(8);

        return mapFundingByCanonical(
                List.of(instrument),
                byCanonical,
                (inst, t) -> funding(inst, t.fundingRate(), next)
        ).stream().findFirst().orElseThrow(() ->
                new ExchangeException("[" + getExchangeType() + "] funding not found for "
                        + instrument.baseAsset() + "/" + instrument.quoteAsset()));
    }

    @Override
    public List<FundingRateData> getFundingRates(List<InstrumentData> instruments) {
        String url = config.getBaseUrl() + "/api/mix/v1/market/tickers?productType=" + config.getProductType();
        BitgetResponse<List<BitgetTickerItem>> resp =
                httpExecutor.get(url, config.getTimeout(), new TypeReference<>() {
                });

        require(resp != null && "00000".equals(resp.code()) && resp.data() != null,
                () -> "Bitget funding error: " + (resp != null ? resp.msg() : "null response"));

        Map<String, BitgetTickerItem> byCanonical = indexByCanonical(resp.data(), BitgetTickerItem::symbol);
        long next = nextFundingAlignedHours(8);

        return mapFundingByCanonical(
                instruments,
                byCanonical,
                (inst, t) -> funding(inst, t.fundingRate(), next)
        );
    }

    @Override
    public ExchangeType getExchangeType() {
        return ExchangeType.BITGET;
    }

    @Override
    public Boolean isEnabled() {
        return config.isEnabled();
    }
}
