package net.protsenko.fundy.app.service;

import net.protsenko.fundy.app.dto.TickerData;
import net.protsenko.fundy.app.dto.TradingInstrument;
import net.protsenko.fundy.app.exception.ExchangeException;
import net.protsenko.fundy.app.exchange.ExchangeClient;
import net.protsenko.fundy.app.exchange.ExchangeClientFactory;
import net.protsenko.fundy.app.exchange.ExchangeType;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
public class MarketDataService {

    private final ExchangeClientFactory factory;

    public MarketDataService(ExchangeClientFactory factory) {
        this.factory = factory;
    }

    public List<TradingInstrument> getAvailableInstruments(ExchangeType type) {
        ExchangeClient client = client(type);
        return client.getAvailableInstruments();
    }

    public TickerData getTicker(ExchangeType type, TradingInstrument instrument) {
        return client(type).getTicker(Objects.requireNonNull(instrument));
    }

    public List<TickerData> getTickers(ExchangeType type, List<TradingInstrument> instruments) {
        return client(type).getTickers(Objects.requireNonNull(instruments));
    }

    private ExchangeClient client(ExchangeType type) {
        ExchangeClient c = factory.getClient(type);
        if (!c.isEnabled()) {
            throw new ExchangeException("Биржа " + type + " отключена конфигурацией");
        }
        return c;
    }
}
