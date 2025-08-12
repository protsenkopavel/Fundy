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
import net.protsenko.fundy.app.exchange.support.ExchangeMappingSupport;
import net.protsenko.fundy.app.props.CoinexConfig;
import net.protsenko.fundy.app.utils.HttpExecutor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class CoinexExchangeClient implements ExchangeClient, ExchangeMappingSupport {

    private final HttpExecutor httpExecutor;
    private final CoinexConfig config;

    @Override
    public List<InstrumentData> getInstruments() {
        String url = config.getBaseUrl() + "/perpetual/v1/market/list";
        CoinexResponse<List<CoinexContractItem>> resp =
                httpExecutor.get(url, config.getTimeout(), new TypeReference<>() {
                });

        require(resp != null && resp.code() == 0 && resp.data() != null,
                () -> "CoinEx instruments error: " + (resp != null ? resp.message() : "null"));

        return resp.data().stream()
                .filter(CoinexContractItem::available)
                .filter(i -> i.type() == 1)
                .map(c -> instrument(
                        c.stock(),
                        c.money(),
                        InstrumentType.PERPETUAL,
                        c.name()
                ))
                .toList();
    }

    @Override
    public TickerData getTicker(InstrumentData instrument) {
        String url = config.getBaseUrl() + "/perpetual/v1/market/ticker/all";
        CoinexResponse<CoinexTickerAllData> resp =
                httpExecutor.get(url, config.getTimeout(), new TypeReference<>() {
                });

        require(resp != null && resp.code() == 0 && resp.data() != null && resp.data().ticker() != null,
                () -> "CoinEx ticker/all error: " + (resp != null ? resp.message() : "null"));

        Map<String, CoinexTickerItem> all = resp.data().ticker();

        Map<String, Map.Entry<String, CoinexTickerItem>> byCanonical = indexByCanonical(
                all.entrySet().stream().toList(),
                Map.Entry::getKey
        );

        return mapTickersByCanonical(
                List.of(instrument),
                byCanonical,
                (inst, e) -> {
                    CoinexTickerItem t = e.getValue();
                    return ticker(
                            inst,
                            t.last(),
                            t.buy(),
                            t.sell(),
                            t.high(),
                            t.low(),
                            t.vol()
                    );
                }
        ).stream().findFirst().orElseThrow(() ->
                new ExchangeException("[" + getExchangeType() + "] ticker not found for "
                        + instrument.baseAsset() + "/" + instrument.quoteAsset()));
    }

    @Override
    public List<TickerData> getTickers(List<InstrumentData> instruments) {
        String url = config.getBaseUrl() + "/perpetual/v1/market/ticker/all";
        CoinexResponse<CoinexTickerAllData> resp =
                httpExecutor.get(url, config.getTimeout(), new TypeReference<>() {
                });

        require(resp != null && resp.code() == 0 && resp.data() != null && resp.data().ticker() != null,
                () -> "CoinEx ticker/all error: " + (resp != null ? resp.message() : "null"));

        Map<String, CoinexTickerItem> all = resp.data().ticker();

        Map<String, Map.Entry<String, CoinexTickerItem>> byCanonical = indexByCanonical(
                all.entrySet().stream().toList(),
                Map.Entry::getKey
        );

        return mapTickersByCanonical(
                instruments,
                byCanonical,
                (inst, e) -> {
                    CoinexTickerItem t = e.getValue();
                    return ticker(
                            inst,
                            t.last(),
                            t.buy(),
                            t.sell(),
                            t.high(),
                            t.low(),
                            t.vol()
                    );
                }
        );
    }

    @Override
    public FundingRateData getFundingRate(InstrumentData instrument) {
        String url = config.getBaseUrl() + "/perpetual/v1/market/ticker/all";
        CoinexResponse<CoinexTickerAllData> resp =
                httpExecutor.get(url, config.getTimeout(), new TypeReference<>() {
                });

        require(resp != null && resp.code() == 0 && resp.data() != null && resp.data().ticker() != null,
                () -> "CoinEx ticker/all error: " + (resp != null ? resp.message() : "null"));

        Map<String, CoinexTickerItem> all = resp.data().ticker();

        Map<String, Map.Entry<String, CoinexTickerItem>> byCanonical = indexByCanonical(
                all.entrySet().stream().toList(),
                Map.Entry::getKey
        );

        return mapFundingByCanonical(
                List.of(instrument),
                byCanonical,
                (inst, e) -> {
                    CoinexTickerItem t = e.getValue();
                    return funding(inst, t.fundingRateLast(), calcNextFundingMs(t.fundingTime()));
                }
        ).stream().findFirst().orElseThrow(() ->
                new ExchangeException("[" + getExchangeType() + "] funding not found for "
                        + instrument.baseAsset() + "/" + instrument.quoteAsset()));
    }

    @Override
    public List<FundingRateData> getFundingRates(List<InstrumentData> instruments) {
        String url = config.getBaseUrl() + "/perpetual/v1/market/ticker/all";
        CoinexResponse<CoinexTickerAllData> resp =
                httpExecutor.get(url, config.getTimeout(), new TypeReference<>() {
                });

        require(resp != null && resp.code() == 0 && resp.data() != null && resp.data().ticker() != null,
                () -> "CoinEx ticker/all error: " + (resp != null ? resp.message() : "null"));

        Map<String, CoinexTickerItem> all = resp.data().ticker();

        Map<String, Map.Entry<String, CoinexTickerItem>> byCanonical = indexByCanonical(
                all.entrySet().stream().toList(),
                Map.Entry::getKey
        );

        return mapFundingByCanonical(
                instruments,
                byCanonical,
                (inst, e) -> {
                    CoinexTickerItem t = e.getValue();
                    return funding(inst, t.fundingRateLast(), calcNextFundingMs(t.fundingTime()));
                }
        );
    }

    @Override
    public ExchangeType getExchangeType() {
        return ExchangeType.COINEX;
    }

    @Override
    public Boolean isEnabled() {
        return config.isEnabled();
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