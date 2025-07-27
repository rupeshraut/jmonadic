package org.jmonadic.resilience;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import org.jmonadic.patterns.Result;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A high-performance Circuit Breaker implementation with configurable failure thresholds,
 * timeout periods, and automatic recovery mechanisms.
 * 
 * The Circuit Breaker pattern prevents cascading failures by temporarily disabling
 * calls to a failing service, allowing it time to recover while providing fast
 * failure responses to callers.
 * 
 * States:
 * - CLOSED: Normal operation, calls are allowed through
 * - OPEN: Circuit is open, calls fail immediately
 * - HALF_OPEN: Testing if service has recovered
 */
public class CircuitBreaker {
    
    private static final Logger logger = LoggerFactory.getLogger(CircuitBreaker.class);
    
    private final String name;
    private final int failureThreshold;
    private final int successThreshold;
    private final Duration timeout;
    private final Duration waitDurationInOpenState;
    
    private final AtomicReference<State> state = new AtomicReference<>(State.CLOSED);
    private final AtomicInteger failureCount = new AtomicInteger(0);
    private final AtomicInteger successCount = new AtomicInteger(0);
    private final AtomicLong lastFailureTime = new AtomicLong(0);
    private final AtomicInteger callsInHalfOpenState = new AtomicInteger(0);
    
    public enum State {
        CLOSED, OPEN, HALF_OPEN
    }
    
    public static class Builder {
        private String name = "CircuitBreaker";
        private int failureThreshold = 5;
        private int successThreshold = 3;
        private Duration timeout = Duration.ofSeconds(1);
        private Duration waitDurationInOpenState = Duration.ofSeconds(30);
        
        public Builder name(String name) {
            this.name = name;
            return this;
        }
        
        public Builder failureThreshold(int failureThreshold) {
            this.failureThreshold = failureThreshold;
            return this;
        }
        
        public Builder successThreshold(int successThreshold) {
            this.successThreshold = successThreshold;
            return this;
        }
        
        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }
        
        public Builder waitDurationInOpenState(Duration waitDurationInOpenState) {
            this.waitDurationInOpenState = waitDurationInOpenState;
            return this;
        }
        
        public CircuitBreaker build() {
            return new CircuitBreaker(this);
        }
    }
    
    private CircuitBreaker(Builder builder) {
        this.name = builder.name;
        this.failureThreshold = builder.failureThreshold;
        this.successThreshold = builder.successThreshold;
        this.timeout = builder.timeout;
        this.waitDurationInOpenState = builder.waitDurationInOpenState;
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    /**
     * Executes the supplier within the circuit breaker protection.
     */
    public <T> Result<T, CircuitBreakerException> execute(Supplier<T> supplier) {
        if (!canExecute()) {
            return Result.failure(new CircuitBreakerException("Circuit breaker is OPEN for: " + name));
        }
        
        try {
            long startTime = System.nanoTime();
            T result = supplier.get();
            long executionTime = System.nanoTime() - startTime;
            
            if (Duration.ofNanos(executionTime).compareTo(timeout) > 0) {
                onFailure();
                return Result.failure(new CircuitBreakerException("Operation timed out: " + name));
            }
            
            onSuccess();
            return Result.success(result);
            
        } catch (Exception e) {
            onFailure();
            return Result.failure(new CircuitBreakerException("Operation failed: " + name, e));
        }
    }
    
    /**
     * Executes a runnable within the circuit breaker protection.
     */
    public Result<Void, CircuitBreakerException> executeVoid(Runnable runnable) {
        return execute(() -> {
            runnable.run();
            return null;
        });
    }
    
    private boolean canExecute() {
        State currentState = state.get();
        
        switch (currentState) {
            case CLOSED:
                return true;
                
            case OPEN:
                if (shouldAttemptReset()) {
                    state.compareAndSet(State.OPEN, State.HALF_OPEN);
                    callsInHalfOpenState.set(0);
                    logger.info("Circuit breaker {} transitioning to HALF_OPEN", name);
                    return true;
                }
                return false;
                
            case HALF_OPEN:
                return callsInHalfOpenState.incrementAndGet() <= successThreshold;
                
            default:
                return false;
        }
    }
    
    private boolean shouldAttemptReset() {
        return Instant.now().toEpochMilli() - lastFailureTime.get() >= waitDurationInOpenState.toMillis();
    }
    
    private void onSuccess() {
        failureCount.set(0);
        
        State currentState = state.get();
        if (currentState == State.HALF_OPEN) {
            int currentSuccessCount = successCount.incrementAndGet();
            if (currentSuccessCount >= successThreshold) {
                state.set(State.CLOSED);
                successCount.set(0);
                logger.info("Circuit breaker {} recovered and transitioned to CLOSED", name);
            }
        }
    }
    
    private void onFailure() {
        lastFailureTime.set(Instant.now().toEpochMilli());
        
        State currentState = state.get();
        
        if (currentState == State.HALF_OPEN) {
            state.set(State.OPEN);
            successCount.set(0);
            logger.warn("Circuit breaker {} failed during HALF_OPEN, transitioning back to OPEN", name);
            return;
        }
        
        int failures = failureCount.incrementAndGet();
        if (failures >= failureThreshold) {
            state.compareAndSet(State.CLOSED, State.OPEN);
            logger.warn("Circuit breaker {} threshold exceeded ({}), transitioning to OPEN", name, failures);
        }
    }
    
    /**
     * Gets the current state of the circuit breaker.
     */
    public State getState() {
        return state.get();
    }
    
    /**
     * Gets the current failure count.
     */
    public int getFailureCount() {
        return failureCount.get();
    }
    
    /**
     * Gets the current success count (used in HALF_OPEN state).
     */
    public int getSuccessCount() {
        return successCount.get();
    }
    
    /**
     * Manually resets the circuit breaker to CLOSED state.
     */
    public void reset() {
        state.set(State.CLOSED);
        failureCount.set(0);
        successCount.set(0);
        callsInHalfOpenState.set(0);
        logger.info("Circuit breaker {} manually reset to CLOSED", name);
    }
    
    /**
     * Gets circuit breaker metrics for monitoring.
     */
    public Metrics getMetrics() {
        return new Metrics(
            name,
            state.get(),
            failureCount.get(),
            successCount.get(),
            lastFailureTime.get()
        );
    }
    
    public record Metrics(
        String name,
        State state,
        int failureCount,
        int successCount,
        long lastFailureTime
    ) {}
    
    /**
     * Exception thrown when circuit breaker is open or operations fail.
     */
    public static class CircuitBreakerException extends Exception {
        public CircuitBreakerException(String message) {
            super(message);
        }
        
        public CircuitBreakerException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
