package net.protsenko.fundy.app.service;

import lombok.RequiredArgsConstructor;
import net.protsenko.fundy.app.dto.TickerData;
import net.protsenko.fundy.app.dto.TradingInstrument;
import net.protsenko.fundy.app.dto.TradingInstrumentRequest;
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

    public List<TradingInstrument> getAvailableInstruments(ExchangeType type) {
        ExchangeClient client = client(type);
        return client.getAvailableInstruments();
    }

    public TickerData getTicker(ExchangeType ex, String base, String quote) {
        TradingInstrument inst = resolver.resolve(ex, base, quote);
        return client(ex).getTicker(inst);
    }

    public List<TickerData> getTickers(ExchangeType ex, List<TradingInstrumentRequest> reqs) {
        List<TradingInstrument> instruments = reqs.stream()
                .map(r -> resolver.resolve(ex, r.base(), r.quote()))
                .toList();
        return client(ex).getTickers(instruments);
    }

    private ExchangeClient client(ExchangeType ex) {
        ExchangeClient c = factory.getClient(ex);
        if (!c.isEnabled()) throw new ExchangeException("Биржа отключена: " + ex);
        return c;
    }
}
