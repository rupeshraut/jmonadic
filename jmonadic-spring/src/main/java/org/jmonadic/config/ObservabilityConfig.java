package org.jmonadic.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import org.jmonadic.observability.ExceptionMetrics;

/**
 * Configuration for observability components.
 */
@Configuration
public class ObservabilityConfig {
    
    @Bean
    @ConditionalOnMissingBean
    public MeterRegistry meterRegistry() {
        return new SimpleMeterRegistry();
    }
    
    @Bean
    public ExceptionMetrics exceptionMetrics(MeterRegistry meterRegistry) {
        return ExceptionMetrics.builder()
            .meterRegistry(meterRegistry)
            .applicationName("exception-showcase")
            .version("1.0.0")
            .build();
    }
}