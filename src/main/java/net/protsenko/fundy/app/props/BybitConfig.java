package net.protsenko.fundy.app.props;

import lombok.Getter;
import lombok.Setter;
import net.protsenko.fundy.app.exchange.ExchangeConfig;
import net.protsenko.fundy.app.exchange.ExchangeType;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "fundy.exchanges.bybit")
public class BybitConfig implements ExchangeConfig {
    private String apiKey;
    private String secretKey;
    private String baseUrl = "https://api.bybit.com";
    private int timeout = 10;
    private boolean enabled = true;

    @Override
    public ExchangeType getExchangeType() {
        return ExchangeType.BYBIT;
    }
}
