package net.protsenko.fundy.app.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Data
@Validated
@ConfigurationProperties(prefix = "fundy.cache.exchange")
public class ExchangeCacheProperties {
    private Duration instrumentsTtl = Duration.ofMinutes(30);
    private long instrumentsMaxSize = 5_000;

    private Duration tickersTtl = Duration.ofSeconds(2);
    private long tickersMaxSize = 50_000;

    private Duration fundingTtl = Duration.ofSeconds(90);
    private long fundingMaxSize = 50_000;
}