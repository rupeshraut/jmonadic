package org.jmonadic.resilience;

import java.time.Duration;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.jmonadic.patterns.Result;
import org.jmonadic.resilience.adapters.Resilience4jAdapter;

import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.vavr.control.Try;

/**
 * Production-ready retry policy implementation using Resilience4j.
 * 
 * Provides the same JMonadic Result<T, E> API while leveraging Resilience4j's
 * enterprise-grade retry mechanisms with advanced backoff strategies.
 * 
 * Features:
 * - Exponential and linear backoff strategies
 * - Custom retry predicates and exception handling
 * - Interval functions for complex backoff patterns
 * - Comprehensive metrics and events
 * - Thread-safe operation
 * - Integration with Spring Boot actuator
 * 
 * Usage:
 * <pre>
 * Resilience4jRetryPolicy retry = Resilience4jRetryPolicy.builder()
 *     .name("userService")
 *     .maxAttempts(3)
 *     .waitDuration(Duration.ofSeconds(1))
 *     .exponentialBackoff(2.0)
 *     .retryOnException(ex -> ex instanceof IOException)
 *     .build();
 * 
 * Result<User, Exception> result = retry.execute(() -> userService.getUser(id));
 * </pre>
 */
public class Resilience4jRetryPolicy {
    
    private final Retry retry;
    private final String name;
    
    public static class Builder {
        private String name = "RetryPolicy";
        private int maxAttempts = 3;
        private Duration waitDuration = Duration.ofMillis(500);
        private boolean useExponentialBackoff = false;
        private double backoffMultiplier = 2.0;
        private Predicate<Exception> retryOnExceptionPredicate = ex -> true;
        private Predicate<Object> retryOnResultPredicate = result -> false;
        private boolean failAfterMaxAttempts = true;
        
        public Builder name(String name) {
            this.name = name;
            return this;
        }
        
        public Builder maxAttempts(int maxAttempts) {
            this.maxAttempts = maxAttempts;
            return this;
        }
        
        public Builder waitDuration(Duration waitDuration) {
            this.waitDuration = waitDuration;
            return this;
        }
        
        public Builder exponentialBackoff(double multiplier) {
            this.useExponentialBackoff = true;
            this.backoffMultiplier = multiplier;
            return this;
        }
        
        public Builder exponentialBackoff(double multiplier, Duration maxWaitDuration) {
            this.useExponentialBackoff = true;
            this.backoffMultiplier = multiplier;
            return this;
        }
        
        public Builder retryOnException(Predicate<Exception> retryOnExceptionPredicate) {
            this.retryOnExceptionPredicate = retryOnExceptionPredicate;
            return this;
        }
        
        public Builder retryOnResult(Predicate<Object> retryOnResultPredicate) {
            this.retryOnResultPredicate = retryOnResultPredicate;
            return this;
        }
        
        public Builder retryOnExceptionOfType(Class<? extends Exception> exceptionClass) {
            this.retryOnExceptionPredicate = exceptionClass::isInstance;
            return this;
        }
        
        public Builder failAfterMaxAttempts(boolean failAfterMaxAttempts) {
            this.failAfterMaxAttempts = failAfterMaxAttempts;
            return this;
        }
        
        @SuppressWarnings("unchecked")
        public Resilience4jRetryPolicy build() {
            RetryConfig.Builder<Object> configBuilder = RetryConfig.custom()
                .maxAttempts(maxAttempts)
                .waitDuration(waitDuration)
                .retryOnException((Predicate<Throwable>) (Predicate<?>) retryOnExceptionPredicate)
                .retryOnResult(retryOnResultPredicate)
                .failAfterMaxAttempts(failAfterMaxAttempts);
            
            if (useExponentialBackoff) {
                configBuilder.intervalFunction(attempt -> 
                    (long) (waitDuration.toMillis() * Math.pow(backoffMultiplier, attempt - 1)));
            }
            
            RetryConfig config = configBuilder.build();
            Retry retry = Retry.of(name, config);
            
            return new Resilience4jRetryPolicy(retry, name);
        }
    }
    
    private Resilience4jRetryPolicy(Retry retry, String name) {
        this.retry = retry;
        this.name = name;
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    /**
     * Executes the supplier with retry logic, returning Result<T, Exception>.
     */
    public <T> Result<T, Exception> execute(Supplier<T> supplier) {
        Supplier<T> decoratedSupplier = Retry.decorateSupplier(retry, supplier);
        Try<T> result = Try.ofSupplier(decoratedSupplier);
        return Resilience4jAdapter.tryToResult(result);
    }
    
    /**
     * Executes the supplier with retry logic and custom error mapping.
     */
    public <T, E> Result<T, E> execute(Supplier<T> supplier, Function<Exception, E> errorMapper) {
        Supplier<T> decoratedSupplier = Retry.decorateSupplier(retry, supplier);
        Try<T> result = Try.ofSupplier(decoratedSupplier);
        
        return result.fold(
            exception -> Result.<T, E>failure(errorMapper.apply((Exception) exception)),
            success -> Result.<T, E>success(success)
        );
    }
    
    /**
     * Executes a runnable with retry logic.
     */
    public Result<Void, Exception> executeVoid(Runnable runnable) {
        return execute(() -> {
            runnable.run();
            return null;
        });
    }
    
    /**
     * Creates a decorator that applies retry logic to any supplier.
     */
    public <T> Function<Supplier<T>, Result<T, Exception>> decorator() {
        return supplier -> execute(supplier);
    }
    
    /**
     * Creates a decorator with custom error mapping.
     */
    public <T, E> Function<Supplier<T>, Result<T, E>> decorator(Function<Exception, E> errorMapper) {
        return supplier -> execute(supplier, errorMapper);
    }
    
    /**
     * Combines this retry policy with a circuit breaker for maximum resilience.
     */
    public <T> Result<T, Exception> executeWithCircuitBreaker(
            Supplier<T> supplier, Resilience4jCircuitBreaker circuitBreaker) {
        
        // Apply circuit breaker first, then retry
        Supplier<T> cbDecorated = io.github.resilience4j.circuitbreaker.CircuitBreaker.decorateSupplier(
            circuitBreaker.getCircuitBreaker(), supplier);
        
        return execute(cbDecorated);
    }
    
    /**
     * Gets the name of the retry policy.
     */
    public String getName() {
        return name;
    }
    
    /**
     * Gets the underlying Resilience4j retry for advanced configuration.
     */
    public Retry getRetry() {
        return retry;
    }
    
    /**
     * Gets retry metrics.
     */
    public Retry.Metrics getMetrics() {
        return retry.getMetrics();
    }
    
    /**
     * Common preset configurations.
     */
    public static class Presets {
        
        public static Resilience4jRetryPolicy defaultRetry() {
            return builder().build();
        }
        
        public static Resilience4jRetryPolicy quickRetry() {
            return builder()
                .name("QuickRetry")
                .maxAttempts(2)
                .waitDuration(Duration.ofMillis(100))
                .exponentialBackoff(1.5)
                .build();
        }
        
        public static Resilience4jRetryPolicy resilientRetry() {
            return builder()
                .name("ResilientRetry")
                .maxAttempts(5)
                .waitDuration(Duration.ofMillis(200))
                .exponentialBackoff(2.0, Duration.ofSeconds(10))
                .build();
        }
        
        public static Resilience4jRetryPolicy networkRetry() {
            return builder()
                .name("NetworkRetry")
                .maxAttempts(3)
                .waitDuration(Duration.ofSeconds(1))
                .exponentialBackoff(2.0, Duration.ofSeconds(30))
                .retryOnException(ex -> 
                    ex instanceof java.net.SocketTimeoutException ||
                    ex instanceof java.net.ConnectException ||
                    ex instanceof java.io.IOException)
                .build();
        }
        
        public static Resilience4jRetryPolicy databaseRetry() {
            return builder()
                .name("DatabaseRetry")
                .maxAttempts(3)
                .waitDuration(Duration.ofMillis(500))
                .retryOnException(ex -> 
                    ex instanceof java.sql.SQLException ||
                    ex.getClass().getName().contains("DataAccessException"))
                .build();
        }
        
        public static Resilience4jRetryPolicy webServiceRetry() {
            return builder()
                .name("WebServiceRetry")
                .maxAttempts(4)
                .waitDuration(Duration.ofMillis(300))
                .exponentialBackoff(1.8, Duration.ofSeconds(15))
                .retryOnException(ex -> 
                    ex instanceof java.net.SocketTimeoutException ||
                    ex instanceof java.net.ConnectException ||
                    ex.getClass().getName().contains("HttpServerErrorException"))
                .build();
        }
    }
}