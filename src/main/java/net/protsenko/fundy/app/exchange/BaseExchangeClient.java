package net.protsenko.fundy.app.exchange;

public interface BaseExchangeClient {
    ExchangeType getExchangeType();
    Boolean isEnabled();
}