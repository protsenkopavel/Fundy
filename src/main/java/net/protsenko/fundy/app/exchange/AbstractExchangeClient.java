package net.protsenko.fundy.app.exchange;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import net.protsenko.fundy.app.dto.rs.InstrumentData;
import net.protsenko.fundy.app.dto.rs.TickerData;
import net.protsenko.fundy.app.exception.ExchangeException;
import org.springframework.cache.annotation.Cacheable;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
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
    public Boolean isEnabled() {
        return config.isEnabled();
    }

    @Override
    @Cacheable(cacheNames = "exchange-instruments",
            key = "#root.target.exchangeType",
            cacheManager = "caffeineCacheManager")
    public List<InstrumentData> getAvailableInstruments() {
        return fetchAvailableInstruments();
    }

    protected abstract List<InstrumentData> fetchAvailableInstruments();

    @Override
    public abstract TickerData getTicker(InstrumentData instrument);

    protected <R> R sendRequest(HttpRequest request, Class<R> responseType) {
        HttpResponse<String> resp = send(request);
        return parseBody(resp, responseType);
    }

    protected <R> R sendRequest(HttpRequest request, TypeReference<R> typeRef) {
        HttpResponse<String> resp = send(request);
        return parseBody(resp, typeRef);
    }

    private HttpResponse<String> send(HttpRequest request) {
        try {
            HttpResponse<String> resp = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() >= 400) {
                String body = resp.body() == null ? "" : resp.body();
                log.error("HTTP {} {} -> {} {}",
                        request.method(), request.uri(), resp.statusCode(), body);
            }
            return resp;
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            log.warn("HTTP {} {} interrupted: {}",
                    request.method(), request.uri(), ie.getMessage());
            throw new ExchangeException("API request interrupted", ie);
        } catch (HttpTimeoutException te) {
            log.warn("HTTP {} {} timeout after {}s",
                    request.method(), request.uri(), config.getTimeout());
            throw new ExchangeException("API request timed out", te);
        } catch (IOException ioe) {
            log.warn("HTTP {} {} failed (IO): {}",
                    request.method(), request.uri(), ioe.getMessage());
            throw new ExchangeException("API request failed", ioe);
        }
    }

    private <R> R parseBody(HttpResponse<String> response, Class<R> type) {
        validateStatus(response);
        try {
            return mapper.readValue(response.body(), type);
        } catch (JsonProcessingException e) {
            String snippet = response.body() == null ? "" : response.body();
            log.error("JSON parse failed ({}): {}", type.getSimpleName(), snippet);
            throw new ExchangeException("JSON parse failed", e);
        }
    }

    private <R> R parseBody(HttpResponse<String> response, TypeReference<R> typeRef) {
        validateStatus(response);
        try {
            return mapper.readValue(response.body(), typeRef);
        } catch (JsonProcessingException e) {
            String snippet = response.body() == null ? "" : response.body();
            log.error("JSON parse failed (TypeReference): {}", snippet);
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
