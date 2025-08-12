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
import net.protsenko.fundy.app.exchange.support.ExchangeMappingSupport;
import net.protsenko.fundy.app.props.HtxConfig;
import net.protsenko.fundy.app.utils.HttpExecutor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

import static net.protsenko.fundy.app.utils.ExchangeUtils.toLong;

@Slf4j
@Component
@RequiredArgsConstructor
public class HtxExchangeClient implements ExchangeClient, ExchangeMappingSupport {

    private final HttpExecutor httpExecutor;
    private final HtxConfig config;

    @Override
    public List<InstrumentData> getInstruments() {
        String url = config.getBaseUrl() + "/linear-swap-api/v1/swap_contract_info";
        HtxResp<List<HtxContractItem>> resp =
                httpExecutor.get(url, config.getTimeout(), new TypeReference<>() {
                });

        require(resp != null && "ok".equalsIgnoreCase(resp.status()) && resp.data() != null,
                () -> "HTX instruments error: " + (resp != null ? resp.status() : "null"));

        return resp.data().stream()
                .filter(c -> c.contractStatus() == 1)
                .map(c -> {
                    String code = c.contractCode();
                    String[] p = code.split("-");
                    String base = p.length > 0 ? p[0] : c.symbol();
                    String quote = p.length > 1 ? p[1] : c.tradePartition();
                    return instrument(base, quote, InstrumentType.PERPETUAL, code);
                })
                .toList();
    }

    @Override
    public TickerData getTicker(InstrumentData instrument) {
        String url = config.getBaseUrl() + "/linear-swap-ex/market/detail/batch_merged";
        HtxBatchResp resp = httpExecutor.get(url, config.getTimeout(), HtxBatchResp.class);

        require(resp != null && "ok".equalsIgnoreCase(resp.status()) && resp.ticks() != null,
                () -> "HTX batch tickers error: " + (resp != null ? resp.status() : "null"));

        Map<String, HtxBatchResp.Tick> byCanonical =
                indexByCanonical(resp.ticks(), HtxBatchResp.Tick::contractCode);

        return mapTickersByCanonical(
                List.of(instrument),
                byCanonical,
                (inst, t) -> ticker(
                        inst,
                        String.valueOf(t.close()),
                        "0", "0",
                        String.valueOf(t.high()),
                        String.valueOf(t.low()),
                        String.valueOf(t.vol())
                )
        ).stream().findFirst().orElseThrow(() ->
                new ExchangeException("[" + getExchangeType() + "] ticker not found for "
                        + instrument.baseAsset() + "/" + instrument.quoteAsset()));
    }

    @Override
    public List<TickerData> getTickers(List<InstrumentData> instruments) {
        String url = config.getBaseUrl() + "/linear-swap-ex/market/detail/batch_merged";
        HtxBatchResp resp = httpExecutor.get(url, config.getTimeout(), HtxBatchResp.class);

        require(resp != null && "ok".equalsIgnoreCase(resp.status()) && resp.ticks() != null,
                () -> "HTX batch tickers error: " + (resp != null ? resp.status() : "null"));

        Map<String, HtxBatchResp.Tick> byCanonical =
                indexByCanonical(resp.ticks(), HtxBatchResp.Tick::contractCode);

        return mapTickersByCanonical(
                instruments,
                byCanonical,
                (inst, t) -> ticker(
                        inst,
                        String.valueOf(t.close()),
                        "0", "0",
                        String.valueOf(t.high()),
                        String.valueOf(t.low()),
                        String.valueOf(t.vol())
                )
        );
    }

    @Override
    public FundingRateData getFundingRate(InstrumentData instrument) {
        String url = config.getBaseUrl() + "/linear-swap-api/v1/swap_batch_funding_rate";
        HtxResp<List<HtxFundingItem>> resp =
                httpExecutor.get(url, config.getTimeout(), new TypeReference<>() {
                });

        require(resp != null && "ok".equalsIgnoreCase(resp.status()) && resp.data() != null,
                () -> "HTX batch funding error: " + (resp != null ? resp.status() : "null"));

        Map<String, HtxFundingItem> byCanonical =
                indexByCanonical(resp.data(), HtxFundingItem::contractCode);

        return mapFundingByCanonical(
                List.of(instrument),
                byCanonical,
                (inst, f) -> funding(inst, f.fundingRate(), toLong(f.fundingTime()))
        ).stream().findFirst().orElseThrow(() ->
                new ExchangeException("[" + getExchangeType() + "] funding not found for "
                        + instrument.baseAsset() + "/" + instrument.quoteAsset()));
    }

    @Override
    public List<FundingRateData> getFundingRates(List<InstrumentData> instruments) {
        String url = config.getBaseUrl() + "/linear-swap-api/v1/swap_batch_funding_rate";
        HtxResp<List<HtxFundingItem>> resp =
                httpExecutor.get(url, config.getTimeout(), new TypeReference<>() {
                });

        require(resp != null && "ok".equalsIgnoreCase(resp.status()) && resp.data() != null,
                () -> "HTX batch funding error: " + (resp != null ? resp.status() : "null"));

        Map<String, HtxFundingItem> byCanonical =
                indexByCanonical(resp.data(), HtxFundingItem::contractCode);

        return mapFundingByCanonical(
                instruments,
                byCanonical,
                (inst, f) -> funding(inst, f.fundingRate(), toLong(f.fundingTime()))
        );
    }

    @Override
    public ExchangeType getExchangeType() {
        return ExchangeType.HTX;
    }

    @Override
    public Boolean isEnabled() {
        return config.isEnabled();
    }
}
