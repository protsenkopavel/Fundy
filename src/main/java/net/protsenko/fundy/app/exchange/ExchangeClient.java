package net.protsenko.fundy.app.exchange;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import net.protsenko.fundy.app.dto.TickerData;
import net.protsenko.fundy.app.dto.TradingInstrument;
import net.protsenko.fundy.app.exception.ExchangeException;
import org.springframework.cache.annotation.Cacheable;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

public abstract class ExchangeClient<T extends ExchangeConfig> {
    protected final T config;
    protected final HttpClient httpClient;
    protected final ObjectMapper mapper;
    protected final InstrumentSymbolConverter converter;

    protected ExchangeClient(T config, InstrumentSymbolConverter converter) {
        this.config = Objects.requireNonNull(config);
        this.converter = converter;
        this.httpClient = createHttpClient();
        this.mapper = createObjectMapper();
    }

    @Cacheable(value = "exchange-instruments", key = "#root.target.config.exchangeType")
    public List<TradingInstrument> getAvailableInstruments() {
        return fetchAvailableInstruments();
    }

    protected abstract List<TradingInstrument> fetchAvailableInstruments();

    public abstract TickerData getTicker(TradingInstrument instrument);

    public List<TickerData> getTickers(List<TradingInstrument> instruments) {
        return instruments.stream()
                .map(this::getTicker)
                .toList();
    }

    public CompletableFuture<TickerData> getTickerAsync(TradingInstrument instrument) {
        return CompletableFuture.supplyAsync(() -> getTicker(instrument));
    }

    public List<CompletableFuture<TickerData>> getTickersAsync(List<TradingInstrument> instruments) {
        return instruments.stream()
                .map(this::getTickerAsync)
                .toList();
    }

    protected HttpClient createHttpClient() {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(config.getTimeout()))
                .build();
    }

    protected ObjectMapper createObjectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    protected <R> R sendRequest(HttpRequest request, Class<R> responseType) {
        try {
            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString()
            );

            if (response.statusCode() != 200) {
                throw new ExchangeException("HTTP error: " + response.statusCode());
            }

            return mapper.readValue(response.body(), responseType);
        } catch (Exception e) {
            throw new ExchangeException("API request failed", e);
        }
    }

    protected <R> CompletableFuture<R> sendRequestAsync(HttpRequest request, Class<R> responseType) {
        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() != 200) {
                        throw new ExchangeException("HTTP error: " + response.statusCode());
                    }
                    try {
                        return mapper.readValue(response.body(), responseType);
                    } catch (Exception e) {
                        throw new ExchangeException("API request failed", e);
                    }
                });
    }

    protected T getConfig() {
        return config;
    }
}
