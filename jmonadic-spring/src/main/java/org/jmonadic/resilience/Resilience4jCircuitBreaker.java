package org.jmonadic.resilience;

import java.time.Duration;
import java.util.function.Function;
import java.util.function.Supplier;

import org.jmonadic.patterns.Result;
import org.jmonadic.resilience.adapters.Resilience4jAdapter;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.vavr.control.Try;

/**
 * Production-ready circuit breaker implementation using Resilience4j.
 * 
 * Provides the same JMonadic Result<T, E> API while leveraging Resilience4j's
 * battle-tested circuit breaker implementation for enterprise workloads.
 * 
 * Features:
 * - Sliding window failure rate calculation
 * - Configurable wait duration and thresholds
 * - Comprehensive metrics and events
 * - Thread-safe operation
 * - Integration with Spring Boot actuator
 * 
 * Usage:
 * <pre>
 * Resilience4jCircuitBreaker cb = Resilience4jCircuitBreaker.builder()
 *     .name("userService")
 *     .failureRateThreshold(50.0f)
 *     .waitDurationInOpenState(Duration.ofSeconds(30))
 *     .build();
 * 
 * Result<User, Exception> result = cb.execute(() -> userService.getUser(id));
 * </pre>
 */
public class Resilience4jCircuitBreaker {
    
    private final CircuitBreaker circuitBreaker;
    private final String name;
    
    public static class Builder {
        private String name = "CircuitBreaker";
        private float failureRateThreshold = 50.0f;
        private int minimumNumberOfCalls = 10;
        private Duration waitDurationInOpenState = Duration.ofSeconds(30);
        private int slidingWindowSize = 100;
        private CircuitBreakerConfig.SlidingWindowType slidingWindowType = 
            CircuitBreakerConfig.SlidingWindowType.COUNT_BASED;
        private int permittedNumberOfCallsInHalfOpenState = 3;
        private boolean automaticTransitionFromOpenToHalfOpenEnabled = true;
        
        public Builder name(String name) {
            this.name = name;
            return this;
        }
        
        public Builder failureRateThreshold(float failureRateThreshold) {
            this.failureRateThreshold = failureRateThreshold;
            return this;
        }
        
        public Builder minimumNumberOfCalls(int minimumNumberOfCalls) {
            this.minimumNumberOfCalls = minimumNumberOfCalls;
            return this;
        }
        
        public Builder waitDurationInOpenState(Duration waitDurationInOpenState) {
            this.waitDurationInOpenState = waitDurationInOpenState;
            return this;
        }
        
        public Builder slidingWindowSize(int slidingWindowSize) {
            this.slidingWindowSize = slidingWindowSize;
            return this;
        }
        
        public Builder slidingWindowType(CircuitBreakerConfig.SlidingWindowType slidingWindowType) {
            this.slidingWindowType = slidingWindowType;
            return this;
        }
        
        public Builder permittedNumberOfCallsInHalfOpenState(int permittedNumberOfCallsInHalfOpenState) {
            this.permittedNumberOfCallsInHalfOpenState = permittedNumberOfCallsInHalfOpenState;
            return this;
        }
        
        public Builder automaticTransitionFromOpenToHalfOpenEnabled(boolean enabled) {
            this.automaticTransitionFromOpenToHalfOpenEnabled = enabled;
            return this;
        }
        
        public Resilience4jCircuitBreaker build() {
            CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(failureRateThreshold)
                .minimumNumberOfCalls(minimumNumberOfCalls)
                .waitDurationInOpenState(waitDurationInOpenState)
                .slidingWindowSize(slidingWindowSize)
                .slidingWindowType(slidingWindowType)
                .permittedNumberOfCallsInHalfOpenState(permittedNumberOfCallsInHalfOpenState)
                .automaticTransitionFromOpenToHalfOpenEnabled(automaticTransitionFromOpenToHalfOpenEnabled)
                .build();
            
            CircuitBreaker circuitBreaker = CircuitBreakerRegistry.ofDefaults()
                .circuitBreaker(name, config);
            
            return new Resilience4jCircuitBreaker(circuitBreaker, name);
        }
    }
    
    private Resilience4jCircuitBreaker(CircuitBreaker circuitBreaker, String name) {
        this.circuitBreaker = circuitBreaker;
        this.name = name;
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    /**
     * Executes the supplier with circuit breaker protection, returning Result<T, Exception>.
     */
    public <T> Result<T, Exception> execute(Supplier<T> supplier) {
        Supplier<T> decoratedSupplier = CircuitBreaker.decorateSupplier(circuitBreaker, supplier);
        Try<T> result = Try.ofSupplier(decoratedSupplier);
        return Resilience4jAdapter.tryToResult(result);
    }
    
    /**
     * Executes the supplier with circuit breaker protection and custom error mapping.
     */
    public <T, E> Result<T, E> execute(Supplier<T> supplier, Function<Exception, E> errorMapper) {
        Supplier<T> decoratedSupplier = CircuitBreaker.decorateSupplier(circuitBreaker, supplier);
        Try<T> result = Try.ofSupplier(decoratedSupplier);
        
        return result.fold(
            exception -> Result.<T, E>failure(errorMapper.apply((Exception) exception)),
            success -> Result.<T, E>success(success)
        );
    }
    
    /**
     * Executes a runnable with circuit breaker protection.
     */
    public Result<Void, Exception> executeVoid(Runnable runnable) {
        return execute(() -> {
            runnable.run();
            return null;
        });
    }
    
    /**
     * Creates a decorator that applies circuit breaker protection to any supplier.
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
     * Gets the current state of the circuit breaker.
     */
    public CircuitBreaker.State getState() {
        return circuitBreaker.getState();
    }
    
    /**
     * Gets the name of the circuit breaker.
     */
    public String getName() {
        return name;
    }
    
    /**
     * Gets the underlying Resilience4j circuit breaker for advanced configuration.
     */
    public CircuitBreaker getCircuitBreaker() {
        return circuitBreaker;
    }
    
    /**
     * Gets circuit breaker metrics.
     */
    public CircuitBreaker.Metrics getMetrics() {
        return circuitBreaker.getMetrics();
    }
    
    /**
     * Transitions the circuit breaker to CLOSED state.
     */
    public void transitionToClosedState() {
        circuitBreaker.transitionToClosedState();
    }
    
    /**
     * Transitions the circuit breaker to OPEN state.
     */
    public void transitionToOpenState() {
        circuitBreaker.transitionToOpenState();
    }
    
    /**
     * Transitions the circuit breaker to HALF_OPEN state.
     */
    public void transitionToHalfOpenState() {
        circuitBreaker.transitionToHalfOpenState();
    }
    
    /**
     * Common preset configurations.
     */
    public static class Presets {
        
        public static Resilience4jCircuitBreaker defaultCircuitBreaker() {
            return builder().build();
        }
        
        public static Resilience4jCircuitBreaker resilientCircuitBreaker() {
            return builder()
                .name("ResilientCircuitBreaker")
                .failureRateThreshold(60.0f)
                .minimumNumberOfCalls(5)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .slidingWindowSize(50)
                .permittedNumberOfCallsInHalfOpenState(5)
                .build();
        }
        
        public static Resilience4jCircuitBreaker networkCircuitBreaker() {
            return builder()
                .name("NetworkCircuitBreaker")
                .failureRateThreshold(40.0f)
                .minimumNumberOfCalls(3)
                .waitDurationInOpenState(Duration.ofSeconds(60))
                .slidingWindowSize(20)
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.TIME_BASED)
                .permittedNumberOfCallsInHalfOpenState(3)
                .build();
        }
        
        public static Resilience4jCircuitBreaker fastFailCircuitBreaker() {
            return builder()
                .name("FastFailCircuitBreaker")
                .failureRateThreshold(30.0f)
                .minimumNumberOfCalls(2)
                .waitDurationInOpenState(Duration.ofSeconds(10))
                .slidingWindowSize(10)
                .permittedNumberOfCallsInHalfOpenState(2)
                .build();
        }
    }
}