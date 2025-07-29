package org.jmonadic.config;

import java.time.Duration;

import org.jmonadic.resilience.Resilience4jCircuitBreaker;
import org.jmonadic.resilience.Resilience4jRetryPolicy;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;

/**
 * Spring Boot auto-configuration for JMonadic Resilience4j integration.
 * 
 * Provides default beans for circuit breakers and retry policies that can be
 * easily customized through application properties.
 * 
 * Configuration properties:
 * <pre>
 * jmonadic.resilience4j.circuit-breaker.enabled=true
 * jmonadic.resilience4j.circuit-breaker.failure-rate-threshold=50
 * jmonadic.resilience4j.circuit-breaker.minimum-number-of-calls=10
 * jmonadic.resilience4j.circuit-breaker.wait-duration-in-open-state=30s
 * 
 * jmonadic.resilience4j.retry.enabled=true
 * jmonadic.resilience4j.retry.max-attempts=3
 * jmonadic.resilience4j.retry.wait-duration=500ms
 * jmonadic.resilience4j.retry.exponential-backoff-multiplier=2.0
 * </pre>
 */
@AutoConfiguration
@ConditionalOnClass({Resilience4jCircuitBreaker.class, Resilience4jRetryPolicy.class})
@EnableConfigurationProperties({
    Resilience4jAutoConfiguration.CircuitBreakerProperties.class,
    Resilience4jAutoConfiguration.RetryProperties.class
})
public class Resilience4jAutoConfiguration {
    
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(prefix = "jmonadic.resilience4j.circuit-breaker", name = "enabled", havingValue = "true", matchIfMissing = true)
    static class CircuitBreakerConfiguration {
        
        @Bean
        @ConditionalOnMissingBean(name = "defaultCircuitBreaker")
        public Resilience4jCircuitBreaker defaultCircuitBreaker(CircuitBreakerProperties properties) {
            return Resilience4jCircuitBreaker.builder()
                .name("defaultCircuitBreaker")
                .failureRateThreshold(properties.getFailureRateThreshold())
                .minimumNumberOfCalls(properties.getMinimumNumberOfCalls())
                .waitDurationInOpenState(properties.getWaitDurationInOpenState())
                .slidingWindowSize(properties.getSlidingWindowSize())
                .slidingWindowType(properties.getSlidingWindowType())
                .permittedNumberOfCallsInHalfOpenState(properties.getPermittedNumberOfCallsInHalfOpenState())
                .automaticTransitionFromOpenToHalfOpenEnabled(properties.isAutomaticTransitionFromOpenToHalfOpenEnabled())
                .build();
        }
        
        @Bean
        @ConditionalOnMissingBean(name = "networkCircuitBreaker")
        public Resilience4jCircuitBreaker networkCircuitBreaker() {
            return Resilience4jCircuitBreaker.Presets.networkCircuitBreaker();
        }
        
        @Bean
        @ConditionalOnMissingBean(name = "fastFailCircuitBreaker")
        public Resilience4jCircuitBreaker fastFailCircuitBreaker() {
            return Resilience4jCircuitBreaker.Presets.fastFailCircuitBreaker();
        }
    }
    
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(prefix = "jmonadic.resilience4j.retry", name = "enabled", havingValue = "true", matchIfMissing = true)
    static class RetryConfiguration {
        
        @Bean
        @ConditionalOnMissingBean(name = "defaultRetryPolicy")
        public Resilience4jRetryPolicy defaultRetryPolicy(RetryProperties properties) {
            Resilience4jRetryPolicy.Builder builder = Resilience4jRetryPolicy.builder()
                .name("defaultRetryPolicy")
                .maxAttempts(properties.getMaxAttempts())
                .waitDuration(properties.getWaitDuration());
            
            if (properties.getExponentialBackoffMultiplier() > 0) {
                if (properties.getMaxWaitDuration() != null) {
                    builder.exponentialBackoff(properties.getExponentialBackoffMultiplier(), 
                                              properties.getMaxWaitDuration());
                } else {
                    builder.exponentialBackoff(properties.getExponentialBackoffMultiplier());
                }
            }
            
            return builder.build();
        }
        
        @Bean
        @ConditionalOnMissingBean(name = "networkRetryPolicy")
        public Resilience4jRetryPolicy networkRetryPolicy() {
            return Resilience4jRetryPolicy.Presets.networkRetry();
        }
        
        @Bean
        @ConditionalOnMissingBean(name = "databaseRetryPolicy")
        public Resilience4jRetryPolicy databaseRetryPolicy() {
            return Resilience4jRetryPolicy.Presets.databaseRetry();
        }
        
        @Bean
        @ConditionalOnMissingBean(name = "webServiceRetryPolicy")
        public Resilience4jRetryPolicy webServiceRetryPolicy() {
            return Resilience4jRetryPolicy.Presets.webServiceRetry();
        }
    }
    
    @ConfigurationProperties(prefix = "jmonadic.resilience4j.circuit-breaker")
    public static class CircuitBreakerProperties {
        private boolean enabled = true;
        private float failureRateThreshold = 50.0f;
        private int minimumNumberOfCalls = 10;
        private Duration waitDurationInOpenState = Duration.ofSeconds(30);
        private int slidingWindowSize = 100;
        private CircuitBreakerConfig.SlidingWindowType slidingWindowType = 
            CircuitBreakerConfig.SlidingWindowType.COUNT_BASED;
        private int permittedNumberOfCallsInHalfOpenState = 3;
        private boolean automaticTransitionFromOpenToHalfOpenEnabled = true;
        
        public boolean isEnabled() {
            return enabled;
        }
        
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
        
        public float getFailureRateThreshold() {
            return failureRateThreshold;
        }
        
        public void setFailureRateThreshold(float failureRateThreshold) {
            this.failureRateThreshold = failureRateThreshold;
        }
        
        public int getMinimumNumberOfCalls() {
            return minimumNumberOfCalls;
        }
        
        public void setMinimumNumberOfCalls(int minimumNumberOfCalls) {
            this.minimumNumberOfCalls = minimumNumberOfCalls;
        }
        
        public Duration getWaitDurationInOpenState() {
            return waitDurationInOpenState;
        }
        
        public void setWaitDurationInOpenState(Duration waitDurationInOpenState) {
            this.waitDurationInOpenState = waitDurationInOpenState;
        }
        
        public int getSlidingWindowSize() {
            return slidingWindowSize;
        }
        
        public void setSlidingWindowSize(int slidingWindowSize) {
            this.slidingWindowSize = slidingWindowSize;
        }
        
        public CircuitBreakerConfig.SlidingWindowType getSlidingWindowType() {
            return slidingWindowType;
        }
        
        public void setSlidingWindowType(CircuitBreakerConfig.SlidingWindowType slidingWindowType) {
            this.slidingWindowType = slidingWindowType;
        }
        
        public int getPermittedNumberOfCallsInHalfOpenState() {
            return permittedNumberOfCallsInHalfOpenState;
        }
        
        public void setPermittedNumberOfCallsInHalfOpenState(int permittedNumberOfCallsInHalfOpenState) {
            this.permittedNumberOfCallsInHalfOpenState = permittedNumberOfCallsInHalfOpenState;
        }
        
        public boolean isAutomaticTransitionFromOpenToHalfOpenEnabled() {
            return automaticTransitionFromOpenToHalfOpenEnabled;
        }
        
        public void setAutomaticTransitionFromOpenToHalfOpenEnabled(boolean automaticTransitionFromOpenToHalfOpenEnabled) {
            this.automaticTransitionFromOpenToHalfOpenEnabled = automaticTransitionFromOpenToHalfOpenEnabled;
        }
    }
    
    @ConfigurationProperties(prefix = "jmonadic.resilience4j.retry")
    public static class RetryProperties {
        private boolean enabled = true;
        private int maxAttempts = 3;
        private Duration waitDuration = Duration.ofMillis(500);
        private double exponentialBackoffMultiplier = 0.0; // 0 means disabled
        private Duration maxWaitDuration;
        
        public boolean isEnabled() {
            return enabled;
        }
        
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
        
        public int getMaxAttempts() {
            return maxAttempts;
        }
        
        public void setMaxAttempts(int maxAttempts) {
            this.maxAttempts = maxAttempts;
        }
        
        public Duration getWaitDuration() {
            return waitDuration;
        }
        
        public void setWaitDuration(Duration waitDuration) {
            this.waitDuration = waitDuration;
        }
        
        public double getExponentialBackoffMultiplier() {
            return exponentialBackoffMultiplier;
        }
        
        public void setExponentialBackoffMultiplier(double exponentialBackoffMultiplier) {
            this.exponentialBackoffMultiplier = exponentialBackoffMultiplier;
        }
        
        public Duration getMaxWaitDuration() {
            return maxWaitDuration;
        }
        
        public void setMaxWaitDuration(Duration maxWaitDuration) {
            this.maxWaitDuration = maxWaitDuration;
        }
    }
}