package net.protsenko.fundy.app.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@Configuration
public class AsyncConfig {
    @Bean
    public Executor exchangeExecutor() {
        return Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
    }

    @Bean
    public TaskExecutor applicationTaskExecutor() {
        return new SimpleAsyncTaskExecutor(r -> Thread.ofVirtual().name("vt-", 0).unstarted(r));
    }
}
