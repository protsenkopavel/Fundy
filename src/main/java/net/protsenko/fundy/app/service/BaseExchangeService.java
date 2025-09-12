package net.protsenko.fundy.app.service;

import lombok.extern.slf4j.Slf4j;
import net.protsenko.fundy.app.exception.ExchangeException;
import net.protsenko.fundy.app.exchange.ExchangeClientFactory;
import net.protsenko.fundy.app.exchange.ExchangeType;
import net.protsenko.fundy.app.exchange.FuturesExchangeClient;
import net.protsenko.fundy.app.exchange.SpotExchangeClient;

import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;

@Slf4j
abstract class BaseExchangeService {

    protected final ExchangeClientFactory factory;

    protected BaseExchangeService(ExchangeClientFactory factory) {
        this.factory = factory;
    }

    protected static <T> Stream<T> safe(Stream<T> s) {
        return s == null ? Stream.empty() : s;
    }

    protected SpotExchangeClient spotClient(ExchangeType exchangeType) {
        SpotExchangeClient c = factory.getSpotClient(exchangeType);
        if (!c.isEnabled()) throw new ExchangeException("Биржа отключена: " + exchangeType);
        return c;
    }

    protected FuturesExchangeClient futuresClient(ExchangeType exchangeType) {
        FuturesExchangeClient c = factory.getFuturesClient(exchangeType);
        if (!c.isEnabled()) throw new ExchangeException("Биржа отключена: " + exchangeType);
        return c;
    }

    protected <T> Stream<T> acrossSpot(Set<ExchangeType> exchanges,
                                       Function<SpotExchangeClient, Stream<T>> fn) {
        return exchanges.parallelStream()
                .flatMap(ex -> {
                    try {
                        return safe(fn.apply(spotClient(ex)));
                    } catch (Exception e) {
                        log.warn("Skip {}: {}", ex, e.getMessage());
                        return Stream.empty();
                    }
                });
    }

    protected <T> Stream<T> acrossFutures(Set<ExchangeType> exchanges,
                                          Function<FuturesExchangeClient, Stream<T>> fn) {
        return exchanges.parallelStream()
                .flatMap(ex -> {
                    try {
                        return safe(fn.apply(futuresClient(ex)));
                    } catch (Exception e) {
                        log.warn("Skip {}: {}", ex, e.getMessage());
                        return Stream.empty();
                    }
                });
    }
}
