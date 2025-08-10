package net.protsenko.fundy.app.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.protsenko.fundy.app.exception.ExchangeException;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class HttpExecutor {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public <T> T get(String url, int timeoutSec, Class<T> type) {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(timeoutSec))
                .GET()
                .build();
        HttpResponse<String> resp = send(req);
        return parseBody(resp, type);
    }

    public <T> T get(String url, int timeoutSec, TypeReference<T> typeRef) {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(timeoutSec))
                .GET()
                .build();
        HttpResponse<String> resp = send(req);
        return parseBody(resp, typeRef);
    }

    public <T> T get(String url, int timeoutSec, Map<String, String> headers, TypeReference<T> typeRef) {
        HttpRequest.Builder b = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(timeoutSec))
                .GET();
        if (headers != null) headers.forEach(b::header);
        HttpResponse<String> resp = send(b.build());
        return parseBody(resp, typeRef);
    }

    public <T> T get(String url, int timeoutSec, Map<String, String> headers, Class<T> type) {
        HttpRequest.Builder b = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(timeoutSec))
                .GET();
        if (headers != null) headers.forEach(b::header);
        HttpResponse<String> resp = send(b.build());
        return parseBody(resp, type);
    }

    private HttpResponse<String> send(HttpRequest request) {
        try {
            HttpResponse<String> resp = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() >= 400) {
                String body = resp.body() == null ? "" : resp.body();
                log.error("HTTP {} {} -> {} {}", request.method(), request.uri(), resp.statusCode(), body);
            }
            validateStatus(resp);
            return resp;
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            log.warn("HTTP {} {} interrupted: {}", request.method(), request.uri(), ie.getMessage());
            throw new ExchangeException("API request interrupted", ie);
        } catch (HttpTimeoutException te) {
            log.warn("HTTP {} {} timeout after {}", request.method(), request.uri(), request.timeout().orElse(Duration.ZERO));
            throw new ExchangeException("API request timed out", te);
        } catch (IOException ioe) {
            log.warn("HTTP {} {} failed (IO): {}", request.method(), request.uri(), ioe.getMessage());
            throw new ExchangeException("API request failed", ioe);
        }
    }

    private void validateStatus(HttpResponse<?> response) {
        int code = response.statusCode();
        if (code < 200 || code >= 300) {
            throw new ExchangeException("HTTP error: " + code + ", body: " + response.body());
        }
    }

    private <R> R parseBody(HttpResponse<String> response, Class<R> type) {
        try {
            return objectMapper.readValue(response.body(), type);
        } catch (JsonProcessingException e) {
            String snippet = response.body() == null ? "" : response.body();
            log.error("JSON parse failed ({}): {}", type.getSimpleName(), snippet);
            throw new ExchangeException("JSON parse failed", e);
        }
    }

    private <R> R parseBody(HttpResponse<String> response, TypeReference<R> typeRef) {
        try {
            return objectMapper.readValue(response.body(), typeRef);
        } catch (JsonProcessingException e) {
            String snippet = response.body() == null ? "" : response.body();
            log.error("JSON parse failed (TypeReference): {}", snippet);
            throw new ExchangeException("JSON parse failed", e);
        }
    }
}