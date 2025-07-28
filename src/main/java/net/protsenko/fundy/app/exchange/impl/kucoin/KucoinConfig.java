package net.protsenko.fundy.app.exchange.impl.kucoin;

import lombok.Getter;
import lombok.Setter;
import net.protsenko.fundy.app.exchange.ExchangeConfig;
import net.protsenko.fundy.app.exchange.ExchangeType;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "fundy.exchanges.kucoin")
public class KucoinConfig implements ExchangeConfig {

    private String apiKey;
    private String secretKey;
    private String baseUrl = "https://api-futures.kucoin.com";
    private int timeout = 10;
    private boolean enabled = true;

    @Override
    public ExchangeType getExchangeType() {
        return ExchangeType.KUCOIN;
    }
}