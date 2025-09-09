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
@ConfigurationProperties(prefix = "fundy.exchanges.mexc")
public class MexcConfig implements ExchangeConfig {
    private String apiKey;
    private String secretKey;
    private String baseUrl = "https://contract.mexc.com";
    private String spotBaseUrl = "https://api.mexc.com";
    private int timeout = 10;
    private boolean enabled = true;

    @Override
    public ExchangeType getExchangeType() {
        return ExchangeType.MEXC;
    }
}
