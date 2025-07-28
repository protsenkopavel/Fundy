package net.protsenko.fundy.app.exchange;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import net.protsenko.fundy.app.dto.TickerData;
import net.protsenko.fundy.app.dto.TradingInstrument;
import net.protsenko.fundy.app.exception.ExchangeException;
import org.springframework.cache.annotation.Cacheable;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

@Slf4j
public abstract class AbstractExchangeClient<T extends ExchangeConfig> implements ExchangeClient {

    protected final T config;
    protected final HttpClient httpClient;
    protected final ObjectMapper mapper;

    protected AbstractExchangeClient(T config) {
        this(config, defaultHttpClient(config), defaultMapper());
    }

    protected AbstractExchangeClient(T config, HttpClient httpClient, ObjectMapper mapper) {
        this.config = Objects.requireNonNull(config);
        this.httpClient = Objects.requireNonNull(httpClient);
        this.mapper = Objects.requireNonNull(mapper);
    }

    protected static HttpClient defaultHttpClient(ExchangeConfig cfg) {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(cfg.getTimeout()))
                .build();
    }

    protected static ObjectMapper defaultMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    @Override
    public boolean isEnabled() {
        return config.isEnabled();
    }

    @Override
    @Cacheable(cacheNames = "exchange-instruments",
            key = "#root.target.exchangeType",
            cacheManager = "caffeineCacheManager")
    public List<TradingInstrument> getAvailableInstruments() {
        return fetchAvailableInstruments();
    }

    protected abstract List<TradingInstrument> fetchAvailableInstruments();

    @Override
    public abstract TickerData getTicker(TradingInstrument instrument);

    protected <R> R sendRequest(HttpRequest request, Class<R> responseType) {
        HttpResponse<String> resp = null;
        try {
            resp = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            logResponse(request, resp);
            return parseBody(resp, responseType);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            log.error("HTTP {} {} interrupted", request.method(), request.uri(), ie);
            throw new ExchangeException("API request interrupted", ie);
        } catch (IOException ioe) {
            log.error("HTTP {} {} failed (IO).", request.method(), request.uri(), ioe);
            throw new ExchangeException("API request failed", ioe);
        }
    }

    protected <R> R sendRequest(HttpRequest request, TypeReference<R> typeRef) {
        HttpResponse<String> resp = null;
        try {
            resp = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            logResponse(request, resp);
            return parseBody(resp, typeRef);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            log.error("HTTP {} {} interrupted", request.method(), request.uri(), ie);
            throw new ExchangeException("API request interrupted", ie);
        } catch (IOException ioe) {
            log.error("HTTP {} {} failed (IO).", request.method(), request.uri(), ioe);
            throw new ExchangeException("API request failed", ioe);
        }
    }

    private void logResponse(HttpRequest req, HttpResponse<String> resp) {
        if (resp.statusCode() >= 400) {
            log.error("HTTP {} {} -> {} {}", req.method(), req.uri(),
                    resp.statusCode(), resp.body());
        }
    }

    private <R> R parseBody(HttpResponse<String> response, Class<R> type) {
        validateStatus(response);
        try {
            return mapper.readValue(response.body(), type);
        } catch (Exception e) {
            throw new ExchangeException("JSON parse failed", e);
        }
    }

    private <R> R parseBody(HttpResponse<String> response, TypeReference<R> typeRef) {
        validateStatus(response);
        try {
            return mapper.readValue(response.body(), typeRef);
        } catch (Exception e) {
            log.error("JSON parse failed. Body: {}", response.body(), e);
            throw new ExchangeException("JSON parse failed", e);
        }
    }

    private void validateStatus(HttpResponse<?> response) {
        int code = response.statusCode();
        if (code < 200 || code >= 300) {
            throw new ExchangeException("HTTP error: " + code + ", body: " + response.body());
        }
    }
}
