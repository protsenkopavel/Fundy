package net.protsenko.fundy.app.exchange;

import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class ExchangeClientFactory {
    private final Map<ExchangeType, SpotExchangeClient> spotRegistry;
    private final Map<ExchangeType, FuturesExchangeClient> futuresRegistry;

    public ExchangeClientFactory(List<SpotExchangeClient> spotClients, List<FuturesExchangeClient> futuresClients) {
        Map<ExchangeType, SpotExchangeClient> spotTmp = spotClients.stream()
                .collect(Collectors.toMap(
                        SpotExchangeClient::getExchangeType,
                        Function.identity(),
                        (existing, _) -> {
                            throw new IllegalStateException(
                                    "Duplicate SpotExchangeClient for type: " + existing.getExchangeType()
                            );
                        },
                        () -> new EnumMap<>(ExchangeType.class)
                ));

        Map<ExchangeType, FuturesExchangeClient> futuresTmp = futuresClients.stream()
                .collect(Collectors.toMap(
                        FuturesExchangeClient::getExchangeType,
                        Function.identity(),
                        (existing, _) -> {
                            throw new IllegalStateException(
                                    "Duplicate FuturesExchangeClient for type: " + existing.getExchangeType()
                            );
                        },
                        () -> new EnumMap<>(ExchangeType.class)
                ));

        this.spotRegistry = Map.copyOf(spotTmp);
        this.futuresRegistry = Map.copyOf(futuresTmp);
    }

    public SpotExchangeClient getSpotClient(ExchangeType type) {
        SpotExchangeClient client = spotRegistry.get(type);
        if (client == null) {
            throw new IllegalArgumentException("Spot client not supported: " + type);
        }
        return client;
    }

    public FuturesExchangeClient getFuturesClient(ExchangeType type) {
        FuturesExchangeClient client = futuresRegistry.get(type);
        if (client == null) {
            throw new IllegalArgumentException("Futures client not supported: " + type);
        }
        return client;
    }
}
