package net.protsenko.fundy.app.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
@EnableCaching
@EnableConfigurationProperties(ExchangeCacheProperties.class)
public class CacheConfig {

    @Bean
    public CacheManager cacheManager(ExchangeCacheProperties p) {
        SimpleCacheManager manager = new SimpleCacheManager();

        Caffeine<Object, Object> instruments = Caffeine.newBuilder()
                .expireAfterWrite(p.getInstrumentsTtl())
                .maximumSize(p.getInstrumentsMaxSize())
                .recordStats();

        Caffeine<Object, Object> tickers = Caffeine.newBuilder()
                .expireAfterWrite(p.getTickersTtl())
                .maximumSize(p.getTickersMaxSize())
                .recordStats();

        Caffeine<Object, Object> funding = Caffeine.newBuilder()
                .expireAfterWrite(p.getFundingTtl())
                .maximumSize(p.getFundingMaxSize())
                .recordStats();

        Caffeine<Object, Object> universe = Caffeine.newBuilder()
                .expireAfterWrite(java.time.Duration.ofHours(24))
                .maximumSize(10)
                .recordStats();

        manager.setCaches(List.of(
                new CaffeineCache("ex-instruments", instruments.build()),
                new CaffeineCache("ex-tickers", tickers.build()),
                new CaffeineCache("ex-funding", funding.build()),
                new CaffeineCache("ex-funding-meta", funding.build()),
                new CaffeineCache("universe-perp-24h", universe.build())
        ));
        return manager;
    }
}
