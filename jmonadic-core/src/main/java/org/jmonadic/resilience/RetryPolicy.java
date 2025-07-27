package org.jmonadic.resilience;

import java.time.Duration;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.jmonadic.patterns.Result;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A resilient retry mechanism with exponential backoff, jitter, and configurable
 * retry policies. Supports both synchronous and asynchronous execution.
 */
public class RetryPolicy {
    
    private static final Logger logger = LoggerFactory.getLogger(RetryPolicy.class);
    private static final ScheduledExecutorService scheduler = 
        new ScheduledThreadPoolExecutor(2, r -> {
            Thread t = new Thread(r, "retry-scheduler");
            t.setDaemon(true);
            return t;
        });
    
    private final String name;
    private final int maxAttempts;
    private final Duration initialDelay;
    private final Duration maxDelay;
    private final double backoffMultiplier;
    private final double jitterFactor;
    private final Predicate<Exception> retryPredicate;
    private final Random random = new Random();
    
    public static class Builder {
        private String name = "RetryPolicy";
        private int maxAttempts = 3;
        private Duration initialDelay = Duration.ofMillis(100);
        private Duration maxDelay = Duration.ofSeconds(30);
        private double backoffMultiplier = 2.0;
        private double jitterFactor = 0.1;
        private Predicate<Exception> retryPredicate = ex -> true;
        
        public Builder name(String name) {
            this.name = name;
            return this;
        }
        
        public Builder maxAttempts(int maxAttempts) {
            this.maxAttempts = maxAttempts;
            return this;
        }
        
        public Builder initialDelay(Duration initialDelay) {
            this.initialDelay = initialDelay;
            return this;
        }
        
        public Builder maxDelay(Duration maxDelay) {
            this.maxDelay = maxDelay;
            return this;
        }
        
        public Builder backoffMultiplier(double backoffMultiplier) {
            this.backoffMultiplier = backoffMultiplier;
            return this;
        }
        
        public Builder jitterFactor(double jitterFactor) {
            this.jitterFactor = jitterFactor;
            return this;
        }
        
        public Builder retryIf(Predicate<Exception> retryPredicate) {
            this.retryPredicate = retryPredicate;
            return this;
        }
        
        public Builder retryIfInstanceOf(Class<? extends Exception> exceptionClass) {
            this.retryPredicate = exceptionClass::isInstance;
            return this;
        }
        
        public RetryPolicy build() {
            return new RetryPolicy(this);
        }
    }
    
    private RetryPolicy(Builder builder) {
        this.name = builder.name;
        this.maxAttempts = builder.maxAttempts;
        this.initialDelay = builder.initialDelay;
        this.maxDelay = builder.maxDelay;
        this.backoffMultiplier = builder.backoffMultiplier;
        this.jitterFactor = builder.jitterFactor;
        this.retryPredicate = builder.retryPredicate;
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    /**
     * Executes the supplier with retry logic.
     */
    public <T> Result<T, Exception> execute(Supplier<T> supplier) {
        Exception lastException = null;
        
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                T result = supplier.get();
                if (attempt > 1) {
                    logger.info("Operation {} succeeded on attempt {}", name, attempt);
                }
                return Result.success(result);
                
            } catch (Exception e) {
                lastException = e;
                
                if (attempt == maxAttempts || !retryPredicate.test(e)) {
                    logger.error("Operation {} failed after {} attempts", name, attempt, e);
                    break;
                }
                
                Duration delay = calculateDelay(attempt);
                logger.warn("Operation {} failed on attempt {}, retrying in {}ms", 
                           name, attempt, delay.toMillis(), e);
                
                try {
                    Thread.sleep(delay.toMillis());
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return Result.failure(new RetryException("Retry interrupted", ie));
                }
            }
        }
        
        return Result.failure(new RetryException(
            String.format("Operation %s failed after %d attempts", name, maxAttempts), 
            lastException));
    }
    
    /**
     * Executes the supplier asynchronously with retry logic.
     */
    public <T> CompletableFuture<Result<T, Exception>> executeAsync(Supplier<T> supplier) {
        return executeAsyncInternal(supplier, 1, null);
    }
    
    private <T> CompletableFuture<Result<T, Exception>> executeAsyncInternal(
            Supplier<T> supplier, int attempt, Exception lastException) {
        
        if (attempt > maxAttempts) {
            return CompletableFuture.completedFuture(
                Result.failure(new RetryException(
                    String.format("Operation %s failed after %d attempts", name, maxAttempts),
                    lastException)));
        }
        
        return CompletableFuture
            .supplyAsync(supplier)
            .thenApply(result -> {
                if (attempt > 1) {
                    logger.info("Async operation {} succeeded on attempt {}", name, attempt);
                }
                return Result.<T, Exception>success(result);
            })
            .exceptionally(throwable -> {
                Exception e = throwable instanceof CompletionException ? 
                    (Exception) throwable.getCause() : (Exception) throwable;
                
                if (attempt == maxAttempts || !retryPredicate.test(e)) {
                    logger.error("Async operation {} failed after {} attempts", name, attempt, e);
                    return Result.failure(e);
                }
                
                Duration delay = calculateDelay(attempt);
                logger.warn("Async operation {} failed on attempt {}, retrying in {}ms", 
                           name, attempt, delay.toMillis(), e);
                
                return null; // Will be handled by the next stage
            })
            .thenCompose(result -> {
                if (result != null) {
                    return CompletableFuture.completedFuture(result);
                }
                
                // Schedule retry
                Duration delay = calculateDelay(attempt);
                CompletableFuture<Result<T, Exception>> future = new CompletableFuture<>();
                
                scheduler.schedule(() -> {
                    executeAsyncInternal(supplier, attempt + 1, lastException)
                        .whenComplete((res, ex) -> {
                            if (ex != null) {
                                future.completeExceptionally(ex);
                            } else {
                                future.complete(res);
                            }
                        });
                }, delay.toMillis(), TimeUnit.MILLISECONDS);
                
                return future;
            });
    }
    
    /**
     * Combines retry policy with circuit breaker for maximum resilience.
     */
    public <T> Result<T, Exception> executeWithCircuitBreaker(
            Supplier<T> supplier, CircuitBreaker circuitBreaker) {
        
        return execute(() -> {
            Result<T, CircuitBreaker.CircuitBreakerException> cbResult = 
                circuitBreaker.execute(supplier);
            
            if (cbResult.isSuccess()) {
                return cbResult.getValue();
            } else {
                throw new RuntimeException(cbResult.getError());
            }
        });
    }
    
    private Duration calculateDelay(int attempt) {
        long baseDelay = (long) (initialDelay.toMillis() * Math.pow(backoffMultiplier, attempt - 1));
        long maxDelayMs = maxDelay.toMillis();
        
        // Apply max delay cap
        baseDelay = Math.min(baseDelay, maxDelayMs);
        
        // Apply jitter
        double jitter = 1 + (random.nextDouble() * 2 - 1) * jitterFactor;
        long finalDelay = (long) (baseDelay * jitter);
        
        return Duration.ofMillis(Math.max(0, finalDelay));
    }
    
    /**
     * Creates a retry policy for common scenarios.
     */
    public static class Presets {
        
        public static RetryPolicy defaultRetry() {
            return builder().build();
        }
        
        public static RetryPolicy quickRetry() {
            return builder()
                .maxAttempts(2)
                .initialDelay(Duration.ofMillis(50))
                .backoffMultiplier(1.5)
                .build();
        }
        
        public static RetryPolicy resilientRetry() {
            return builder()
                .maxAttempts(5)
                .initialDelay(Duration.ofMillis(200))
                .maxDelay(Duration.ofSeconds(10))
                .backoffMultiplier(2.0)
                .jitterFactor(0.2)
                .build();
        }
        
        public static RetryPolicy networkRetry() {
            return builder()
                .name("NetworkRetry")
                .maxAttempts(3)
                .initialDelay(Duration.ofSeconds(1))
                .maxDelay(Duration.ofSeconds(30))
                .backoffMultiplier(2.0)
                .jitterFactor(0.3)
                .retryIf(ex -> ex instanceof java.net.SocketTimeoutException ||
                              ex instanceof java.net.ConnectException ||
                              ex instanceof java.io.IOException)
                .build();
        }
    }
    
    /**
     * Exception thrown when all retry attempts are exhausted.
     */
    public static class RetryException extends Exception {
        public RetryException(String message) {
            super(message);
        }
        
        public RetryException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
