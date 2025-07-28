package net.protsenko.fundy.app.exchange.impl.htx;

import lombok.Getter;
import lombok.Setter;
import net.protsenko.fundy.app.exchange.ExchangeConfig;
import net.protsenko.fundy.app.exchange.ExchangeType;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "fundy.exchanges.htx")
public class HtxConfig implements ExchangeConfig {
    private String apiKey;
    private String secretKey;
    private String baseUrl = "https://api.hbdm.com";
    private int timeout = 10;
    private boolean enabled = true;

    @Override
    public ExchangeType getExchangeType() {
        return ExchangeType.HTX;
    }
}
