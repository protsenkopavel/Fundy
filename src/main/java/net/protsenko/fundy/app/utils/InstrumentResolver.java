package net.protsenko.fundy.app.utils;

import lombok.RequiredArgsConstructor;
import net.protsenko.fundy.app.dto.rs.InstrumentData;
import net.protsenko.fundy.app.exchange.ExchangeClientFactory;
import net.protsenko.fundy.app.exchange.ExchangeType;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class InstrumentResolver {
    private final ExchangeClientFactory factory;

    public InstrumentData resolve(ExchangeType ex, String base, String quote) {
        return factory.getClient(ex).getAvailableInstruments().stream()
                .filter(i -> i.baseAsset().equalsIgnoreCase(base) && i.quoteAsset().equalsIgnoreCase(quote))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Инструмент не найден: " + base + "/" + quote));
    }
}
