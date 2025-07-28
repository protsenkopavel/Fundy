package net.protsenko.fundy.app.exchange.impl.bybit;

import net.protsenko.fundy.app.dto.InstrumentType;
import net.protsenko.fundy.app.dto.TickerData;
import net.protsenko.fundy.app.dto.TradingInstrument;
import net.protsenko.fundy.app.exception.ExchangeException;
import net.protsenko.fundy.app.exchange.AbstractExchangeClient;
import net.protsenko.fundy.app.exchange.ExchangeType;
import net.protsenko.fundy.app.exchange.InstrumentSymbolConverter;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpRequest;
import java.util.ArrayList;
import java.util.List;

@Component
public class BybitExchangeClient extends AbstractExchangeClient<BybitConfig> {

    public BybitExchangeClient(BybitConfig config) {
        super(config);
    }

    @Override
    public ExchangeType getExchangeType() {
        return ExchangeType.BYBIT;
    }

    @Override
    protected List<TradingInstrument> fetchAvailableInstruments() {
        String url = config.getBaseUrl() + "/v5/market/instruments-info?category=linear";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();

        BybitInstrumentsResponse response = sendRequest(request, BybitInstrumentsResponse.class);

        if (response == null || response.retCode() != 0 || response.result() == null) {
            throw new ExchangeException("Bybit instruments error: " + (response != null ? response.retMsg() : "null response"));
        }

        List<TradingInstrument> res = new ArrayList<>();
        for (BybitInstrumentItem item : response.result().list()) {
            if (!"Trading".equalsIgnoreCase(item.status())) continue;
            res.add(new TradingInstrument(item.baseCoin(), item.quoteCoin(), InstrumentType.PERPETUAL));
        }
        return res;
    }

    @Override
    public TickerData getTicker(TradingInstrument instrument) {
        String symbol = InstrumentSymbolConverter.toBybitLinearSymbol(instrument);
        String url = config.getBaseUrl() + "/v5/market/tickers?category=linear&symbol=" + symbol;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();

        BybitTickerResponse response = sendRequest(request, BybitTickerResponse.class);

        if (response == null || response.retCode() != 0 || response.result() == null || response.result().list().isEmpty()) {
            throw new ExchangeException("Bybit ticker error for " + symbol + ": " +
                    (response != null ? response.retMsg() : "null response"));
        }

        BybitTickerItem item = response.result().list().get(0);
        return new TickerData(
                instrument,
                Double.parseDouble(item.lastPrice()),
                Double.parseDouble(item.bid1Price()),
                Double.parseDouble(item.ask1Price()),
                Double.parseDouble(item.highPrice24h()),
                Double.parseDouble(item.lowPrice24h()),
                Double.parseDouble(item.volume24h()),
                System.currentTimeMillis()
        );
    }
}
