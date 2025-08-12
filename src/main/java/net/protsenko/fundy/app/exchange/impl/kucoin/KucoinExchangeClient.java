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
import net.protsenko.fundy.app.exchange.support.ExchangeMappingSupport;
import net.protsenko.fundy.app.props.KucoinConfig;
import net.protsenko.fundy.app.utils.HttpExecutor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class KucoinExchangeClient implements ExchangeClient, ExchangeMappingSupport {

    private final HttpExecutor httpExecutor;
    private final KucoinConfig config;

    @Override
    public List<InstrumentData> getInstruments() {
        String url = config.getBaseUrl() + "/api/v1/contracts/active";
        KucoinContractsResponse resp = httpExecutor.get(url, config.getTimeout(), KucoinContractsResponse.class);

        require(resp != null && resp.data() != null && !resp.data().isEmpty(),
                () -> "KuCoin returned empty contracts list");

        return resp.data().stream()
                .filter(c -> "Open".equalsIgnoreCase(c.status()))
                .map(c -> instrument(
                        c.baseCurrency(),
                        c.quoteCurrency(),
                        InstrumentType.PERPETUAL,
                        c.symbol()
                ))
                .toList();
    }

    @Override
    public TickerData getTicker(InstrumentData instrument) {
        String tUrl = config.getBaseUrl() + "/api/v1/allTickers";
        KucoinAllTickersResponse tResp = httpExecutor.get(tUrl, config.getTimeout(), KucoinAllTickersResponse.class);

        require(tResp != null && "200000".equals(tResp.code()) && tResp.data() != null,
                () -> "KuCoin allTickers error");

        Map<String, KucoinTickerData> byCanonicalTickers =
                indexByCanonical(tResp.data(), KucoinTickerData::symbol);

        String cUrl = config.getBaseUrl() + "/api/v1/contracts/active";
        KucoinContractsResponse cResp = httpExecutor.get(cUrl, config.getTimeout(), KucoinContractsResponse.class);

        require(cResp != null && cResp.data() != null,
                () -> "KuCoin contracts fetch error");

        Map<String, KucoinContractItem> byCanonicalContracts =
                indexByCanonical(cResp.data(), KucoinContractItem::symbol);

        return mapTickersByCanonical(
                List.of(instrument),
                byCanonicalTickers.entrySet().stream().collect(Collectors.toMap(
                        Map.Entry::getKey, Map.Entry::getValue
                )),
                (inst, t) -> {
                    String key = net.protsenko.fundy.app.utils.SymbolNormalizer.canonicalKey(inst);
                    KucoinContractItem c = byCanonicalContracts.get(key);
                    if (c == null) return null;
                    return ticker(
                            inst,
                            t.price(),
                            t.bestBidPrice(),
                            t.bestAskPrice(),
                            c.highPrice(),
                            c.lowPrice(),
                            c.volumeOf24h()
                    );
                }
        ).stream().filter(Objects::nonNull).findFirst().orElseThrow(() ->
                new ExchangeException("[" + getExchangeType() + "] ticker not found for "
                        + instrument.baseAsset() + "/" + instrument.quoteAsset()));
    }

    @Override
    public List<TickerData> getTickers(List<InstrumentData> instruments) {
        String tUrl = config.getBaseUrl() + "/api/v1/allTickers";
        KucoinAllTickersResponse tResp = httpExecutor.get(tUrl, config.getTimeout(), KucoinAllTickersResponse.class);

        require(tResp != null && "200000".equals(tResp.code()) && tResp.data() != null,
                () -> "KuCoin allTickers error");

        Map<String, KucoinTickerData> byCanonicalTickers =
                indexByCanonical(tResp.data(), KucoinTickerData::symbol);

        String cUrl = config.getBaseUrl() + "/api/v1/contracts/active";
        KucoinContractsResponse cResp = httpExecutor.get(cUrl, config.getTimeout(), KucoinContractsResponse.class);

        require(cResp != null && cResp.data() != null,
                () -> "KuCoin contracts fetch error");

        Map<String, KucoinContractItem> byCanonicalContracts =
                indexByCanonical(cResp.data(), KucoinContractItem::symbol);

        return mapTickersByCanonical(
                instruments,
                byCanonicalTickers,
                (inst, t) -> {
                    String key = net.protsenko.fundy.app.utils.SymbolNormalizer.canonicalKey(inst);
                    KucoinContractItem c = byCanonicalContracts.get(key);
                    if (c == null) return null;
                    return ticker(
                            inst,
                            t.price(),
                            t.bestBidPrice(),
                            t.bestAskPrice(),
                            c.highPrice(),
                            c.lowPrice(),
                            c.volumeOf24h()
                    );
                }
        );
    }

    @Override
    public FundingRateData getFundingRate(InstrumentData instrument) {
        String url = config.getBaseUrl() + "/api/v1/contracts/active";
        KucoinContractsResponse resp = httpExecutor.get(url, config.getTimeout(), KucoinContractsResponse.class);

        require(resp != null && resp.data() != null,
                () -> "KuCoin contracts fetch error");

        Map<String, KucoinContractItem> byCanonical =
                indexByCanonical(resp.data(), KucoinContractItem::symbol);

        return mapFundingByCanonical(
                List.of(instrument),
                byCanonical,
                (inst, c) -> funding(inst, c.fundingFeeRate(), c.nextFundingRateDateTime())
        ).stream().findFirst().orElseThrow(() ->
                new ExchangeException("[" + getExchangeType() + "] funding not found for "
                        + instrument.baseAsset() + "/" + instrument.quoteAsset()));
    }

    @Override
    public List<FundingRateData> getFundingRates(List<InstrumentData> instruments) {
        String url = config.getBaseUrl() + "/api/v1/contracts/active";
        KucoinContractsResponse resp = httpExecutor.get(url, config.getTimeout(), KucoinContractsResponse.class);

        require(resp != null && resp.data() != null,
                () -> "KuCoin contracts fetch error");

        Map<String, KucoinContractItem> byCanonical =
                indexByCanonical(resp.data(), KucoinContractItem::symbol);

        return mapFundingByCanonical(
                instruments,
                byCanonical,
                (inst, c) -> "Open".equalsIgnoreCase(c.status())
                        ? funding(inst, c.fundingFeeRate(), c.nextFundingRateDateTime())
                        : null
        ).stream().filter(Objects::nonNull).toList();
    }

    @Override
    public ExchangeType getExchangeType() {
        return ExchangeType.KUCOIN;
    }

    @Override
    public Boolean isEnabled() {
        return config.isEnabled();
    }
}
