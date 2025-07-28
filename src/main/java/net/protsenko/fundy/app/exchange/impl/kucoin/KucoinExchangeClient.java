package net.protsenko.fundy.app.exchange.impl.kucoin;

import net.protsenko.fundy.app.dto.FundingRateData;
import net.protsenko.fundy.app.dto.InstrumentType;
import net.protsenko.fundy.app.dto.TickerData;
import net.protsenko.fundy.app.dto.TradingInstrument;
import net.protsenko.fundy.app.exception.ExchangeException;
import net.protsenko.fundy.app.exchange.AbstractExchangeClient;
import net.protsenko.fundy.app.exchange.ExchangeType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpRequest;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class KucoinExchangeClient extends AbstractExchangeClient<KucoinConfig> {

    private volatile Map<String, TradingInstrument> symbolIndex;
    private volatile Map<String, KucoinContractItem> rawContractIndex;

    public KucoinExchangeClient(KucoinConfig config) {
        super(config);
    }

    @Override
    public ExchangeType getExchangeType() {
        return ExchangeType.KUCOIN;
    }

    @Override
    protected List<TradingInstrument> fetchAvailableInstruments() {
        String url = config.getBaseUrl() + "/api/v1/contracts/active";
        HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();

        KucoinContractsResponse resp = sendRequest(req, KucoinContractsResponse.class);
        if (resp == null || resp.data() == null || resp.data().isEmpty()) {
            throw new ExchangeException("KuCoin returned empty contracts list");
        }

        List<TradingInstrument> list = resp.data().stream()
                .filter(i -> "Open".equalsIgnoreCase(i.status()))
                .map(i -> new TradingInstrument(
                        i.baseCurrency(),
                        i.quoteCurrency(),
                        InstrumentType.PERPETUAL,
                        i.symbol()
                ))
                .toList();

        this.symbolIndex = list.stream()
                .collect(Collectors.toUnmodifiableMap(TradingInstrument::nativeSymbol, Function.identity()));

        this.rawContractIndex = resp.data().stream()
                .collect(Collectors.toUnmodifiableMap(KucoinContractItem::symbol, Function.identity()));

        return list;
    }

    @Override
    public TickerData getTicker(TradingInstrument instrument) {
        String symbol = instrument.nativeSymbol();
        if (symbol == null) {
            symbol = instrument.baseAsset() + instrument.quoteAsset() + "M";
        }

        String url = config.getBaseUrl() + "/api/v1/ticker?symbol=" + symbol;
        HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();

        KucoinTickerResponse resp = sendRequest(req, KucoinTickerResponse.class);
        if (resp == null || !"200000".equals(resp.code()) || resp.data() == null) {
            throw new ExchangeException("KuCoin ticker error: " + (resp != null ? resp.code() : "null"));
        }

        KucoinTickerData td = resp.data();
        KucoinContractItem c = contract(symbol);

        return new TickerData(
                instrument,
                parseD(td.price()),
                parseD(td.bestBidPrice()),
                parseD(td.bestAskPrice()),
                parseD(c.highPrice()),
                parseD(c.lowPrice()),
                parseD(c.volumeOf24h()),
                System.currentTimeMillis()
        );
    }

    @Override
    public FundingRateData getFundingRate(TradingInstrument instrument) {
        KucoinContractItem c = contract(instrument.nativeSymbol());
        return new FundingRateData(
                instrument,
                parseBD(c.fundingFeeRate()),
                c.nextFundingRateDateTime()
        );
    }

    @Override
    public List<FundingRateData> getAllFundingRates() {
        Map<String, TradingInstrument> dict = symbolIndex();
        return rawContractIndex().values().stream()
                .filter(c -> "Open".equalsIgnoreCase(c.status()))
                .map(c -> new FundingRateData(
                        dict.get(c.symbol()),
                        parseBD(c.fundingFeeRate()),
                        c.nextFundingRateDateTime()
                ))
                .toList();
    }

    private KucoinContractItem contract(String symbol) {
        KucoinContractItem c = rawContractIndex().get(symbol);
        if (c == null) throw new ExchangeException("KuCoin contract not found: " + symbol);
        return c;
    }

    private Map<String, TradingInstrument> symbolIndex() {
        Map<String, TradingInstrument> local = symbolIndex;
        if (local == null) {
            fetchAvailableInstruments();
            local = symbolIndex;
        }
        return local;
    }

    private Map<String, KucoinContractItem> rawContractIndex() {
        Map<String, KucoinContractItem> local = rawContractIndex;
        if (local == null) {
            fetchAvailableInstruments();
            local = rawContractIndex;
        }
        return local;
    }

    private double parseD(String s) {
        if (s == null || s.isBlank()) return 0.0;
        try {
            return Double.parseDouble(s);
        } catch (Exception e) {
            return 0.0;
        }
    }

    private BigDecimal parseBD(String s) {
        if (s == null || s.isBlank()) return BigDecimal.ZERO;
        try {
            return new BigDecimal(s);
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }
}
