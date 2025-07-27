package org.jmonadic.observability;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.Tags;

import org.jmonadic.patterns.Result;
import org.jmonadic.resilience.CircuitBreaker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Comprehensive metrics collection for exception handling patterns.
 * 
 * Provides detailed observability into:
 * - Success/failure rates by operation and error type
 * - Performance characteristics and latency distributions
 * - Circuit breaker state transitions and health
 * - Resource utilization and allocation patterns
 */
public class ExceptionMetrics {
    
    private static final Logger logger = LoggerFactory.getLogger(ExceptionMetrics.class);
    
    private final MeterRegistry meterRegistry;
    private final Map<String, Counter> successCounters = new ConcurrentHashMap<>();
    private final Map<String, Counter> failureCounters = new ConcurrentHashMap<>();
    private final Map<String, Timer> operationTimers = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> circuitBreakerStates = new ConcurrentHashMap<>();
    
    public ExceptionMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        initializeBaseMetrics();
    }
    
    private void initializeBaseMetrics() {
        // Register base gauges for overall health
        meterRegistry.gauge("exception.patterns.health.score", this, ExceptionMetrics::calculateHealthScore);
        meterRegistry.gauge("exception.patterns.active.operations", this, metrics -> operationTimers.size());
    }
    
    /**
     * Records a successful operation with timing and context.
     */
    public void recordSuccess(String operation, Duration duration, String... tags) {
        getSuccessCounter(operation, tags).increment();
        getOperationTimer(operation, tags).record(duration);
        
        logger.debug("Recorded success for operation: {} in {}ms", operation, duration.toMillis());
    }
    
    /**
     * Records a failed operation with error details.
     */
    public void recordFailure(String operation, String errorType, Duration duration, String... tags) {
        String[] enhancedTags = enhanceTags(tags, "error.type", errorType);
        getFailureCounter(operation, enhancedTags).increment();
        getOperationTimer(operation, enhancedTags).record(duration);
        
        logger.debug("Recorded failure for operation: {} with error: {} in {}ms", 
                    operation, errorType, duration.toMillis());
    }
    
    /**
     * Records circuit breaker state changes.
     */
    public void recordCircuitBreakerState(String circuitBreakerName, CircuitBreaker.State state) {
        AtomicLong stateGauge = circuitBreakerStates.computeIfAbsent(
            circuitBreakerName, 
            name -> meterRegistry.gauge("circuit.breaker.state", 
                                      Tags.of("name", name), 
                                      new AtomicLong(0))
        );
        
        stateGauge.set(state.ordinal());
        
        Counter stateCounter = Counter.builder("circuit.breaker.transitions")
            .tags("name", circuitBreakerName, "state", state.name())
            .register(meterRegistry);
        stateCounter.increment();
        
        logger.info("Circuit breaker {} transitioned to state: {}", circuitBreakerName, state);
    }
    
    /**
     * Records Result pattern usage with outcome tracking.
     */
    public <T, E> Result<T, E> recordResult(String operation, Result<T, E> result) {
        Timer.Sample sample = Timer.start(meterRegistry);
        
        if (result.isSuccess()) {
            Duration duration = Duration.ofNanos(sample.stop(getOperationTimer(operation)));
            recordSuccess(operation, duration);
        } else {
            String errorType = result.getError() != null ? 
                result.getError().getClass().getSimpleName() : "Unknown";
            Duration duration = Duration.ofNanos(sample.stop(getOperationTimer(operation)));
            recordFailure(operation, errorType, duration);
        }
        
        return result;
    }
    
    /**
     * Wraps a supplier with automatic metrics collection.
     */
    public <T> MetricsWrapper<T> wrap(String operation) {
        return new MetricsWrapper<>(operation, this);
    }
    
    /**
     * Calculates overall system health score based on success rates.
     */
    private double calculateHealthScore() {
        if (successCounters.isEmpty() && failureCounters.isEmpty()) {
            return 1.0; // Perfect health when no operations recorded
        }
        
        double totalSuccess = successCounters.values().stream()
            .mapToDouble(Counter::count)
            .sum();
        
        double totalFailure = failureCounters.values().stream()
            .mapToDouble(Counter::count)
            .sum();
        
        double total = totalSuccess + totalFailure;
        return total > 0 ? totalSuccess / total : 1.0;
    }
    
    /**
     * Gets or creates a success counter for the given operation.
     */
    private Counter getSuccessCounter(String operation, String... tags) {
        String key = operation + ":" + String.join(",", tags);
        return successCounters.computeIfAbsent(key, k -> 
            Counter.builder("exception.patterns.operations")
                .tags(enhanceTags(tags, "operation", operation, "outcome", "success"))
                .register(meterRegistry)
        );
    }
    
    /**
     * Gets or creates a failure counter for the given operation.
     */
    private Counter getFailureCounter(String operation, String... tags) {
        String key = operation + ":" + String.join(",", tags);
        return failureCounters.computeIfAbsent(key, k ->
            Counter.builder("exception.patterns.operations")
                .tags(enhanceTags(tags, "operation", operation, "outcome", "failure"))
                .register(meterRegistry)
        );
    }
    
    /**
     * Gets or creates a timer for the given operation.
     */
    private Timer getOperationTimer(String operation, String... tags) {
        String key = operation + ":" + String.join(",", tags);
        return operationTimers.computeIfAbsent(key, k ->
            Timer.builder("exception.patterns.duration")
                .tags(enhanceTags(tags, "operation", operation))
                .register(meterRegistry)
        );
    }
    
    /**
     * Enhances tag arrays with additional key-value pairs.
     */
    private String[] enhanceTags(String[] existingTags, String... additionalTags) {
        String[] result = new String[existingTags.length + additionalTags.length];
        System.arraycopy(existingTags, 0, result, 0, existingTags.length);
        System.arraycopy(additionalTags, 0, result, existingTags.length, additionalTags.length);
        return result;
    }
    
    /**
     * Wrapper class for automatic metrics collection around operations.
     */
    public static class MetricsWrapper<T> {
        private final String operation;
        private final ExceptionMetrics metrics;
        private final Timer.Sample sample;
        
        private MetricsWrapper(String operation, ExceptionMetrics metrics) {
            this.operation = operation;
            this.metrics = metrics;
            this.sample = Timer.start(metrics.meterRegistry);
        }
        
        /**
         * Executes a supplier with automatic success/failure tracking.
         */
        public Result<T, Exception> execute(Supplier<T> supplier) {
            try {
                T result = supplier.get();
                Duration duration = Duration.ofNanos(sample.stop(metrics.getOperationTimer(operation)));
                metrics.recordSuccess(operation, duration);
                return Result.success(result);
            } catch (Exception e) {
                Duration duration = Duration.ofNanos(sample.stop(metrics.getOperationTimer(operation)));
                metrics.recordFailure(operation, e.getClass().getSimpleName(), duration);
                return Result.failure(e);
            }
        }
        
        /**
         * Executes a Result-returning supplier with metrics tracking.
         */
        public Result<T, Exception> executeResult(Supplier<Result<T, Exception>> supplier) {
            try {
                Result<T, Exception> result = supplier.get();
                Duration duration = Duration.ofNanos(sample.stop(metrics.getOperationTimer(operation)));
                
                if (result.isSuccess()) {
                    metrics.recordSuccess(operation, duration);
                } else {
                    String errorType = result.getError() != null ? 
                        result.getError().getClass().getSimpleName() : "Unknown";
                    metrics.recordFailure(operation, errorType, duration);
                }
                
                return result;
            } catch (Exception e) {
                Duration duration = Duration.ofNanos(sample.stop(metrics.getOperationTimer(operation)));
                metrics.recordFailure(operation, e.getClass().getSimpleName(), duration);
                return Result.failure(e);
            }
        }
    }
    
    /**
     * Builder for creating ExceptionMetrics with custom configuration.
     */
    public static class Builder {
        private MeterRegistry meterRegistry;
        private String applicationName = "exception-showcase";
        private String version = "1.0.0";
        
        public Builder meterRegistry(MeterRegistry meterRegistry) {
            this.meterRegistry = meterRegistry;
            return this;
        }
        
        public Builder applicationName(String applicationName) {
            this.applicationName = applicationName;
            return this;
        }
        
        public Builder version(String version) {
            this.version = version;
            return this;
        }
        
        public ExceptionMetrics build() {
            if (meterRegistry == null) {
                throw new IllegalStateException("MeterRegistry is required");
            }
            
            // Add common tags to registry
            meterRegistry.config().commonTags(
                "application", applicationName,
                "version", version
            );
            
            return new ExceptionMetrics(meterRegistry);
        }
    }
    
    public static Builder builder() {
        return new Builder();
    }
}