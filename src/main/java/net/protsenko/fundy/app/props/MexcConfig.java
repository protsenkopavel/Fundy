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
    private String baseUrl;
    private int timeout;
    private boolean enabled;

    @Override
    public ExchangeType getExchangeType() {
        return ExchangeType.MEXC;
    }
}
