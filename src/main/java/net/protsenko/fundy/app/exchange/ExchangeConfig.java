package net.protsenko.fundy.app.exchange;

import lombok.Getter;

@Getter
public abstract class ExchangeConfig {
    private final String apiKey;
    private final String secretKey;
    private final String baseUrl;
    private final int timeout;
    private final ExchangeType exchangeType;
    private final boolean enabled;

    protected ExchangeConfig(String apiKey, String secretKey, String baseUrl, int timeout, ExchangeType exchangeType, boolean enabled) {
        this.apiKey = apiKey;
        this.secretKey = secretKey;
        this.baseUrl = baseUrl;
        this.timeout = timeout;
        this.exchangeType = exchangeType;
        this.enabled = enabled;
    }
}
