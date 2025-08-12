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
import net.protsenko.fundy.app.exchange.support.ExchangeMappingSupport;
import net.protsenko.fundy.app.props.BingxConfig;
import net.protsenko.fundy.app.utils.HttpExecutor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class BingxExchangeClient implements ExchangeClient, ExchangeMappingSupport {

    private final HttpExecutor httpExecutor;
    private final BingxConfig config;

    @Override
    public List<InstrumentData> getInstruments() {
        String url = config.getBaseUrl() + "/openApi/swap/v2/quote/contracts";
        BingxResponse<List<BingxContractItem>> resp =
                httpExecutor.get(url, config.getTimeout(), new TypeReference<>() {
                });

        require(resp != null && resp.code() == 0 && resp.data() != null,
                () -> "BingX contracts error: " + (resp != null ? resp.msg() : "null"));

        return resp.data().stream()
                .filter(c -> c.status() == 1)
                .map(c -> instrument(
                        c.asset(),
                        c.currency(),
                        InstrumentType.PERPETUAL,
                        c.symbol()
                ))
                .toList();
    }

    @Override
    public TickerData getTicker(InstrumentData instrument) {
        String url = config.getBaseUrl() + "/openApi/swap/v2/quote/ticker";
        BingxResponse<List<BingxTickerItem>> resp =
                httpExecutor.get(url, config.getTimeout(), new TypeReference<>() {
                });

        require(resp != null && resp.code() == 0 && resp.data() != null,
                () -> "BingX ticker error: " + (resp != null ? resp.msg() : "null"));

        Map<String, BingxTickerItem> byCanonical = indexByCanonical(resp.data(), BingxTickerItem::symbol);

        return mapTickersByCanonical(
                List.of(instrument),
                byCanonical,
                (inst, t) -> ticker(
                        inst,
                        t.lastPrice(),
                        t.bestBid(),
                        t.bestAsk(),
                        t.high24h(),
                        t.low24h(),
                        t.volume24h()
                )
        ).stream().findFirst().orElseThrow(() ->
                new ExchangeException("[" + getExchangeType() + "] ticker not found for "
                        + instrument.baseAsset() + "/" + instrument.quoteAsset()));
    }

    @Override
    public List<TickerData> getTickers(List<InstrumentData> instruments) {
        String url = config.getBaseUrl() + "/openApi/swap/v2/quote/ticker";
        BingxResponse<List<BingxTickerItem>> resp =
                httpExecutor.get(url, config.getTimeout(), new TypeReference<>() {
                });

        require(resp != null && resp.code() == 0 && resp.data() != null,
                () -> "BingX tickers error: " + (resp != null ? resp.msg() : "null"));

        Map<String, BingxTickerItem> byCanonical = indexByCanonical(resp.data(), BingxTickerItem::symbol);

        return mapTickersByCanonical(
                instruments,
                byCanonical,
                (inst, t) -> ticker(
                        inst,
                        t.lastPrice(),
                        t.bestBid(),
                        t.bestAsk(),
                        t.high24h(),
                        t.low24h(),
                        t.volume24h()
                )
        );
    }

    @Override
    public FundingRateData getFundingRate(InstrumentData instrument) {
        String url = config.getBaseUrl() + "/openApi/swap/v2/quote/premiumIndex";
        BingxResponse<List<BingxPremiumIndexItem>> resp =
                httpExecutor.get(url, config.getTimeout(), new TypeReference<>() {
                });

        require(resp != null && resp.code() == 0 && resp.data() != null,
                () -> "BingX premiumIndex error: " + (resp != null ? resp.msg() : "null"));

        Map<String, BingxPremiumIndexItem> byCanonical = indexByCanonical(resp.data(), BingxPremiumIndexItem::symbol);

        return mapFundingByCanonical(
                List.of(instrument),
                byCanonical,
                (inst, idx) -> funding(inst, idx.lastFundingRate(), idx.nextFundingTime())
        ).stream().findFirst().orElseThrow(() ->
                new ExchangeException("[" + getExchangeType() + "] funding not found for "
                        + instrument.baseAsset() + "/" + instrument.quoteAsset()));
    }

    @Override
    public List<FundingRateData> getFundingRates(List<InstrumentData> instruments) {
        String url = config.getBaseUrl() + "/openApi/swap/v2/quote/premiumIndex";
        BingxResponse<List<BingxPremiumIndexItem>> resp =
                httpExecutor.get(url, config.getTimeout(), new TypeReference<>() {
                });

        require(resp != null && resp.code() == 0 && resp.data() != null,
                () -> "BingX premiumIndex error: " + (resp != null ? resp.msg() : "null"));

        Map<String, BingxPremiumIndexItem> byCanonical = indexByCanonical(resp.data(), BingxPremiumIndexItem::symbol);

        return mapFundingByCanonical(
                instruments,
                byCanonical,
                (inst, idx) -> funding(inst, idx.lastFundingRate(), idx.nextFundingTime())
        );
    }

    @Override
    public ExchangeType getExchangeType() {
        return ExchangeType.BINGX;
    }

    @Override
    public Boolean isEnabled() {
        return config.isEnabled();
    }
}
