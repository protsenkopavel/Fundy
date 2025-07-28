package net.protsenko.fundy.app.exchange;

public interface ExchangeConfig {
    String getApiKey();

    String getSecretKey();

    String getBaseUrl();

    int getTimeout();

    boolean isEnabled();

    ExchangeType getExchangeType();
}
