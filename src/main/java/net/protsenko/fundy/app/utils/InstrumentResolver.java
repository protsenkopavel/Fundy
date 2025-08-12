package net.protsenko.fundy.app.utils;

import lombok.RequiredArgsConstructor;
import net.protsenko.fundy.app.dto.rs.InstrumentData;
import net.protsenko.fundy.app.exchange.ExchangeClientFactory;
import net.protsenko.fundy.app.exchange.ExchangeType;
import org.springframework.stereotype.Service;

import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InstrumentResolver {
    private final ExchangeClientFactory factory;

    public InstrumentData resolve(ExchangeType ex, String base, String quote) {
        var client = factory.getClient(ex);
        var index = client.getInstruments().stream()
                .collect(Collectors.toMap(
                        SymbolNormalizer::canonicalKey,
                        Function.identity(),
                        (a,b) -> a
                ));

        String key = (base + "/" + quote).toUpperCase(); // под формат canonicalKey
        InstrumentData i = index.get(key);
        if (i == null) throw new IllegalArgumentException("Инструмент не найден: " + base + "/" + quote);
        return i;
    }
}
