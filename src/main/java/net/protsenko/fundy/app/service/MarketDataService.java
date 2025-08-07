package net.protsenko.fundy.app.service;

import lombok.RequiredArgsConstructor;
import net.protsenko.fundy.app.dto.rq.InstrumentsRequest;
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

    public List<InstrumentData> getAvailableInstruments(ExchangeType type) {
        ExchangeClient client = client(type);
        return client.getAvailableInstruments();
    }

    public List<InstrumentData> getAvailableInstruments(InstrumentsRequest req) {
        return req.effectiveExchanges().parallelStream()
                .flatMap(ex -> client(ex).getAvailableInstruments().stream())
                .toList();
    }

    public List<TickerData> getTickers(TickersRequest req) {
        return req.effectiveExchanges().parallelStream()
                .flatMap(ex -> {
                    ExchangeClient c = client(ex);

                    List<InstrumentData> target = req.hasPairs()
                            ? req.pairs().stream()
                            .map(p -> resolver.resolve(ex, p.base(), p.quote()))
                            .toList()
                            : c.getAvailableInstruments();

                    return c.getTickers(target).stream();
                })
                .toList();
    }

    private ExchangeClient client(ExchangeType ex) {
        ExchangeClient c = factory.getClient(ex);
        if (!c.isEnabled()) throw new ExchangeException("Биржа отключена: " + ex);
        return c;
    }
}
