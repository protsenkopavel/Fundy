package net.protsenko.fundy.app.service;

import lombok.RequiredArgsConstructor;
import net.protsenko.fundy.app.dto.rq.InstrumentsRequest;
import net.protsenko.fundy.app.dto.rq.TickerRequest;
import net.protsenko.fundy.app.dto.rq.TickersRequest;
import net.protsenko.fundy.app.dto.rs.InstrumentData;
import net.protsenko.fundy.app.dto.rs.TickerData;
import net.protsenko.fundy.app.exception.ExchangeException;
import net.protsenko.fundy.app.exchange.ExchangeClient;
import net.protsenko.fundy.app.exchange.ExchangeClientFactory;
import net.protsenko.fundy.app.exchange.ExchangeType;
import net.protsenko.fundy.app.utils.InstrumentResolver;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MarketDataService {

    private final ExchangeClientFactory factory;
    private final InstrumentResolver resolver;

    public List<InstrumentData> getAvailableInstruments(InstrumentsRequest instrumentsRequest) {
        return instrumentsRequest.effectiveExchanges().parallelStream()
                .flatMap(exchangeType -> client(exchangeType).getInstruments().stream())
                .toList();
    }

    public List<TickerData> getTicker(TickerRequest tickerRequest) {
        return tickerRequest.effectiveExchanges().parallelStream()
                .map(exchangeType -> {
                    ExchangeClient exchangeClient = client(exchangeType);
                    InstrumentData target = resolver.resolve(exchangeType, tickerRequest.pair().base(), tickerRequest.pair().quote());
                    return exchangeClient.getTicker(target);
                })
                .toList();
    }

    public List<TickerData> getTickers(TickersRequest tickersRequest) {
        return tickersRequest.effectiveExchanges().parallelStream()
                .flatMap(exchangeType -> {
                    ExchangeClient exchangeClient = client(exchangeType);
                    List<InstrumentData> target = tickersRequest.hasPairs()
                            ? tickersRequest.pairs().stream()
                            .map(instrumentPair -> resolver.resolve(exchangeType, instrumentPair.base(), instrumentPair.quote()))
                            .toList()
                            : exchangeClient.getInstruments();
                    return exchangeClient.getTickers(target).stream();
                })
                .toList();
    }

    private ExchangeClient client(ExchangeType exchangeType) {
        ExchangeClient c = factory.getClient(exchangeType);
        if (!c.isEnabled()) throw new ExchangeException("Биржа отключена: " + exchangeType);
        return c;
    }
}
