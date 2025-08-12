package net.protsenko.fundy.app.service;

import lombok.extern.slf4j.Slf4j;
import net.protsenko.fundy.app.exception.ExchangeException;
import net.protsenko.fundy.app.exchange.ExchangeClient;
import net.protsenko.fundy.app.exchange.ExchangeClientFactory;
import net.protsenko.fundy.app.exchange.ExchangeType;

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

    protected ExchangeClient client(ExchangeType exchangeType) {
        ExchangeClient c = factory.getClient(exchangeType);
        if (!c.isEnabled()) throw new ExchangeException("Биржа отключена: " + exchangeType);
        return c;
    }

    protected <T> Stream<T> across(Set<ExchangeType> exchanges,
                                   Function<ExchangeClient, Stream<T>> fn) {
        return exchanges.parallelStream()
                .flatMap(ex -> {
                    try {
                        return safe(fn.apply(client(ex)));
                    } catch (Exception e) {
                        log.warn("Skip {}: {}", ex, e.getMessage());
                        return Stream.empty();
                    }
                });
    }
}
