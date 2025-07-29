package net.protsenko.fundy.notifier.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@Configuration
public class AsyncNotifierConfig {

    @Bean
    public Executor previewExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
