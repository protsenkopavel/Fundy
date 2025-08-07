package net.protsenko.fundy.app.exchange;

import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class ExchangeClientFactory {
    private final Map<ExchangeType, ExchangeClient> registry;

    public ExchangeClientFactory(List<ExchangeClient> clients) {
        Map<ExchangeType, ExchangeClient> tmp = clients.stream()
                .collect(Collectors.toMap(
                        ExchangeClient::getExchangeType,
                        Function.identity(),
                        (existing, _) -> {
                            throw new IllegalStateException(
                                    "Duplicate ExchangeClient for type: " + existing.getExchangeType()
                            );
                        },
                        () -> new EnumMap<>(ExchangeType.class)
                ));

        this.registry = Map.copyOf(tmp);
    }

    @SuppressWarnings("unchecked")
    public <C extends ExchangeClient> C getClient(ExchangeType type) {
        ExchangeClient client = registry.get(type);
        if (client == null) {
            throw new IllegalArgumentException("Не поддерживается: " + type);
        }
        return (C) client;
    }
}
