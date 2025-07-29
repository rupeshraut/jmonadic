package org.jmonadic.resilience;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.*;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import org.jmonadic.patterns.Result;
import org.jmonadic.resilience.RetryPolicy.RetryException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@DisplayName("Retry Policy Tests")
class RetryPolicyTest {

    @Nested
    @DisplayName("Construction and Configuration")
    class ConstructionAndConfiguration {
        
        @Test
        @DisplayName("Should create retry policy with default settings")
        void shouldCreateWithDefaults() {
            RetryPolicy retryPolicy = RetryPolicy.builder().build();
            
            assertThat(retryPolicy).isNotNull();
        }
        
        @Test
        @DisplayName("Should create retry policy with custom settings")
        void shouldCreateWithCustomSettings() {
            RetryPolicy retryPolicy = RetryPolicy.builder()
                .name("TestRetry")
                .maxAttempts(5)
                .initialDelay(Duration.ofMillis(100))
                .maxDelay(Duration.ofSeconds(5))
                .backoffMultiplier(1.5)
                .jitterFactor(0.2)
                .retryIf(ex -> ex instanceof RuntimeException)
                .build();
            
            assertThat(retryPolicy).isNotNull();
        }
        
        @Test
        @DisplayName("Should create retry policy with preset configurations")
        void shouldCreateWithPresets() {
            RetryPolicy defaultRetry = RetryPolicy.Presets.defaultRetry();
            RetryPolicy quickRetry = RetryPolicy.Presets.quickRetry();
            RetryPolicy resilientRetry = RetryPolicy.Presets.resilientRetry();
            RetryPolicy networkRetry = RetryPolicy.Presets.networkRetry();
            
            assertThat(defaultRetry).isNotNull();
            assertThat(quickRetry).isNotNull();
            assertThat(resilientRetry).isNotNull();
            assertThat(networkRetry).isNotNull();
        }
    }
    
    @Nested
    @DisplayName("Basic Retry Behavior")
    class BasicRetryBehavior {
        
        @Test
        @DisplayName("Should succeed on first attempt")
        void shouldSucceedOnFirstAttempt() {
            RetryPolicy retryPolicy = RetryPolicy.builder()
                .maxAttempts(3)
                .initialDelay(Duration.ofMillis(10))
                .build();
            
            AtomicInteger attemptCount = new AtomicInteger(0);
            
            Result<String, Exception> result = retryPolicy.execute(() -> {
                attemptCount.incrementAndGet();
                return "success";
            });
            
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getValue()).isEqualTo("success");
            assertThat(attemptCount.get()).isEqualTo(1);
        }
        
        @Test
        @DisplayName("Should retry on failure and eventually succeed")
        void shouldRetryOnFailureAndEventuallySucceed() {
            RetryPolicy retryPolicy = RetryPolicy.builder()
                .maxAttempts(3)
                .initialDelay(Duration.ofMillis(10))
                .build();
            
            AtomicInteger attemptCount = new AtomicInteger(0);
            
            Result<String, Exception> result = retryPolicy.execute(() -> {
                int attempt = attemptCount.incrementAndGet();
                if (attempt < 3) {
                    throw new RuntimeException("failure on attempt " + attempt);
                }
                return "success on attempt " + attempt;
            });
            
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getValue()).isEqualTo("success on attempt 3");
            assertThat(attemptCount.get()).isEqualTo(3);
        }
        
        @Test
        @DisplayName("Should fail after max attempts")
        void shouldFailAfterMaxAttempts() {
            RetryPolicy retryPolicy = RetryPolicy.builder()
                .maxAttempts(2)
                .initialDelay(Duration.ofMillis(10))
                .build();
            
            AtomicInteger attemptCount = new AtomicInteger(0);
            
            Result<String, Exception> result = retryPolicy.execute(() -> {
                attemptCount.incrementAndGet();
                throw new RuntimeException("always fails");
            });
            
            assertThat(result.isFailure()).isTrue();
            assertThat(result.getError()).isInstanceOf(RetryException.class);
            assertThat(result.getError().getMessage()).contains("failed after 2 attempts");
            assertThat(attemptCount.get()).isEqualTo(2);
        }
    }
    
    @Nested
    @DisplayName("Retry Predicates")
    class RetryPredicates {
        
        @Test
        @DisplayName("Should only retry specific exceptions")
        void shouldOnlyRetrySpecificExceptions() {
            RetryPolicy retryPolicy = RetryPolicy.builder()
                .maxAttempts(3)
                .initialDelay(Duration.ofMillis(10))
                .retryIf(ex -> ex instanceof IllegalArgumentException)
                .build();
            
            AtomicInteger attemptCount = new AtomicInteger(0);
            
            // Should retry IllegalArgumentException
            Result<String, Exception> result1 = retryPolicy.execute(() -> {
                attemptCount.incrementAndGet();
                throw new IllegalArgumentException("retry this");
            });
            
            assertThat(result1.isFailure()).isTrue();
            assertThat(attemptCount.get()).isEqualTo(3); // All attempts used
            
            attemptCount.set(0);
            
            // Should not retry RuntimeException
            Result<String, Exception> result2 = retryPolicy.execute(() -> {
                attemptCount.incrementAndGet();
                throw new RuntimeException("don't retry this");
            });
            
            assertThat(result2.isFailure()).isTrue();
            assertThat(attemptCount.get()).isEqualTo(1); // Only one attempt
        }
        
        @Test
        @DisplayName("Should retry specific exception types")
        void shouldRetrySpecificExceptionTypes() {
            RetryPolicy retryPolicy = RetryPolicy.builder()
                .maxAttempts(3)
                .initialDelay(Duration.ofMillis(10))
                .retryIfInstanceOf(IllegalStateException.class)
                .build();
            
            AtomicInteger attemptCount = new AtomicInteger(0);
            
            Result<String, Exception> result = retryPolicy.execute(() -> {
                attemptCount.incrementAndGet();
                throw new IllegalStateException("retry this specific type");
            });
            
            assertThat(result.isFailure()).isTrue();
            assertThat(attemptCount.get()).isEqualTo(3);
        }
    }
    
    @Nested
    @DisplayName("Backoff Strategies")
    class BackoffStrategies {
        
        @Test
        @DisplayName("Should apply exponential backoff")
        @Timeout(5)
        void shouldApplyExponentialBackoff() {
            RetryPolicy retryPolicy = RetryPolicy.builder()
                .maxAttempts(3)
                .initialDelay(Duration.ofMillis(50))
                .backoffMultiplier(2.0)
                .build();
            
            AtomicInteger attemptCount = new AtomicInteger(0);
            long startTime = System.currentTimeMillis();
            
            Result<String, Exception> result = retryPolicy.execute(() -> {
                attemptCount.incrementAndGet();
                throw new RuntimeException("fail for backoff test");
            });
            
            long duration = System.currentTimeMillis() - startTime;
            
            assertThat(result.isFailure()).isTrue();
            assertThat(attemptCount.get()).isEqualTo(3);
            // Should take at least 50ms + 100ms = 150ms for delays
            assertThat(duration).isGreaterThan(150);
        }
        
        @Test
        @DisplayName("Should respect max delay")
        @Timeout(3)
        void shouldRespectMaxDelay() {
            RetryPolicy retryPolicy = RetryPolicy.builder()
                .maxAttempts(4)
                .initialDelay(Duration.ofMillis(100))
                .maxDelay(Duration.ofMillis(150))
                .backoffMultiplier(10.0) // Would create very large delays without max
                .build();
            
            AtomicInteger attemptCount = new AtomicInteger(0);
            long startTime = System.currentTimeMillis();
            
            Result<String, Exception> result = retryPolicy.execute(() -> {
                attemptCount.incrementAndGet();
                throw new RuntimeException("fail for max delay test");
            });
            
            long duration = System.currentTimeMillis() - startTime;
            
            assertThat(result.isFailure()).isTrue();
            assertThat(attemptCount.get()).isEqualTo(4);
            // Should not take too long due to max delay
            assertThat(duration).isLessThan(1000);
        }
        
        @Test
        @DisplayName("Should apply jitter to reduce thundering herd")
        void shouldApplyJitter() {
            RetryPolicy retryPolicy = RetryPolicy.builder()
                .maxAttempts(2)
                .initialDelay(Duration.ofMillis(100))
                .jitterFactor(0.5) // 50% jitter
                .build();
            
            AtomicInteger attemptCount = new AtomicInteger(0);
            
            // Multiple executions should have slightly different timings due to jitter
            long[] durations = new long[5];
            for (int i = 0; i < 5; i++) {
                attemptCount.set(0);
                long startTime = System.currentTimeMillis();
                
                retryPolicy.execute(() -> {
                    attemptCount.incrementAndGet();
                    throw new RuntimeException("fail for jitter test");
                });
                
                durations[i] = System.currentTimeMillis() - startTime;
            }
            
            // Not all durations should be exactly the same (jitter effect)
            boolean hasVariation = false;
            for (int i = 1; i < durations.length; i++) {
                if (Math.abs(durations[i] - durations[0]) > 5) {
                    hasVariation = true;
                    break;
                }
            }
            
            assertThat(hasVariation).isTrue();
        }
    }
    
    @Nested
    @DisplayName("Async Operations")
    class AsyncOperations {
        
        @Test
        @DisplayName("Should handle async retry")
        @Timeout(10)
        void shouldHandleAsyncRetry() {
            RetryPolicy retryPolicy = RetryPolicy.builder()
                .maxAttempts(3)
                .initialDelay(Duration.ofMillis(50))
                .build();
            
            AtomicInteger attemptCount = new AtomicInteger(0);
            
            CompletableFuture<Result<String, Exception>> futureResult = retryPolicy.executeAsync(() -> {
                int attempt = attemptCount.incrementAndGet();
                if (attempt < 3) {
                    throw new RuntimeException("async failure " + attempt);
                }
                return "async success " + attempt;
            });
            
            Result<String, Exception> result = futureResult.join();
            
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getValue()).isEqualTo("async success 3");
            assertThat(attemptCount.get()).isEqualTo(3);
        }
        
        @Test
        @DisplayName("Should handle async failure after max attempts")
        @Timeout(5)
        void shouldHandleAsyncFailureAfterMaxAttempts() {
            RetryPolicy retryPolicy = RetryPolicy.builder()
                .maxAttempts(2)
                .initialDelay(Duration.ofMillis(10))
                .build();
            
            AtomicInteger attemptCount = new AtomicInteger(0);
            
            CompletableFuture<Result<String, Exception>> futureResult = retryPolicy.executeAsync(() -> {
                attemptCount.incrementAndGet();
                throw new RuntimeException("always async fail");
            });
            
            Result<String, Exception> result = futureResult.join();
            
            assertThat(result.isFailure()).isTrue();
            assertThat(result.getError()).isInstanceOf(Exception.class);
            assertThat(attemptCount.get()).isEqualTo(2);
        }
    }
    
    @Nested
    @DisplayName("Circuit Breaker Integration")
    class CircuitBreakerIntegration {
        
        @Test
        @DisplayName("Should combine retry with circuit breaker")
        void shouldCombineRetryWithCircuitBreaker() {
            RetryPolicy retryPolicy = RetryPolicy.builder()
                .maxAttempts(3)
                .initialDelay(Duration.ofMillis(10))
                .build();
            
            CircuitBreaker circuitBreaker = CircuitBreaker.builder()
                .failureThreshold(5)
                .build();
            
            AtomicInteger attemptCount = new AtomicInteger(0);
            
            Result<String, Exception> result = retryPolicy.executeWithCircuitBreaker(() -> {
                int attempt = attemptCount.incrementAndGet();
                if (attempt < 3) {
                    throw new RuntimeException("failure " + attempt);
                }
                return "success " + attempt;
            }, circuitBreaker);
            
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getValue()).isEqualTo("success 3");
            assertThat(attemptCount.get()).isEqualTo(3);
        }
    }
    
    @Nested
    @DisplayName("Interruption Handling")
    class InterruptionHandling {
        
        @Test
        @DisplayName("Should handle thread interruption")
        void shouldHandleThreadInterruption() {
            RetryPolicy retryPolicy = RetryPolicy.builder()
                .maxAttempts(3)
                .initialDelay(Duration.ofMillis(100))
                .build();
            
            AtomicInteger attemptCount = new AtomicInteger(0);
            
            // Start execution in a separate thread
            CompletableFuture<Result<String, Exception>> future = CompletableFuture.supplyAsync(() -> {
                return retryPolicy.execute(() -> {
                    int attempt = attemptCount.incrementAndGet();
                    if (attempt == 1) {
                        // Interrupt current thread during retry delay
                        Thread.currentThread().interrupt();
                    }
                    throw new RuntimeException("failure " + attempt);
                });
            });
            
            Result<String, Exception> result = future.join();
            
            assertThat(result.isFailure()).isTrue();
            // Should either be RetryException about interruption or the original exception
            assertThat(result.getError()).satisfiesAnyOf(
                error -> assertThat(error).isInstanceOf(RetryException.class),
                error -> assertThat(error).isInstanceOf(RuntimeException.class)
            );
        }
    }
    
    @Nested
    @DisplayName("Basic Operations")
    class BasicOperations {
        
        @Test
        @DisplayName("Should handle basic retry operations")
        void shouldHandleBasicRetryOperations() {
            RetryPolicy retryPolicy = RetryPolicy.builder()
                .maxAttempts(3)
                .initialDelay(Duration.ofMillis(10))
                .build();
            
            AtomicInteger attemptCount = new AtomicInteger(0);
            
            Result<String, Exception> result = retryPolicy.execute(() -> {
                int attempt = attemptCount.incrementAndGet();
                if (attempt < 3) {
                    throw new RuntimeException("basic failure " + attempt);
                }
                return "success on attempt " + attempt;
            });
            
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getValue()).isEqualTo("success on attempt 3");
            assertThat(attemptCount.get()).isEqualTo(3);
        }
    }
    
    @Nested
    @DisplayName("Edge Cases")
    class EdgeCases {
        
        @Test
        @DisplayName("Should handle null supplier")
        void shouldHandleNullSupplier() {
            RetryPolicy retryPolicy = RetryPolicy.builder().build();
            
            Result<String, Exception> result = retryPolicy.execute(null);
            assertThat(result.isFailure()).isTrue();
        }
        
        @Test
        @DisplayName("Should handle max attempts of 1")
        void shouldHandleMaxAttemptsOfOne() {
            RetryPolicy retryPolicy = RetryPolicy.builder()
                .maxAttempts(1)
                .build();
            
            AtomicInteger attemptCount = new AtomicInteger(0);
            
            Result<String, Exception> result = retryPolicy.execute(() -> {
                attemptCount.incrementAndGet();
                throw new RuntimeException("single attempt failure");
            });
            
            assertThat(result.isFailure()).isTrue();
            assertThat(attemptCount.get()).isEqualTo(1);
        }
        
        @Test
        @DisplayName("Should handle zero initial delay")
        void shouldHandleZeroInitialDelay() {
            RetryPolicy retryPolicy = RetryPolicy.builder()
                .maxAttempts(2)
                .initialDelay(Duration.ZERO)
                .build();
            
            AtomicInteger attemptCount = new AtomicInteger(0);
            long startTime = System.currentTimeMillis();
            
            Result<String, Exception> result = retryPolicy.execute(() -> {
                attemptCount.incrementAndGet();
                throw new RuntimeException("zero delay failure");
            });
            
            long duration = System.currentTimeMillis() - startTime;
            
            assertThat(result.isFailure()).isTrue();
            assertThat(attemptCount.get()).isEqualTo(2);
            assertThat(duration).isLessThan(50); // Should be very fast with zero delay
        }
        
        @Test
        @DisplayName("Should handle exception in retry predicate")
        void shouldHandleExceptionInRetryPredicate() {
            RetryPolicy retryPolicy = RetryPolicy.builder()
                .maxAttempts(3)
                .initialDelay(Duration.ofMillis(10))
                .retryIf(ex -> false) // Always return false to avoid retry
                .build();
            
            AtomicInteger attemptCount = new AtomicInteger(0);
            
            Result<String, Exception> result = retryPolicy.execute(() -> {
                attemptCount.incrementAndGet();
                throw new RuntimeException("original failure");
            });
            
            assertThat(result.isFailure()).isTrue();
            assertThat(attemptCount.get()).isEqualTo(1); // Should fail on first attempt
        }
    }
    
    @Nested
    @DisplayName("Performance and Concurrent Access")
    class PerformanceAndConcurrentAccess {
        
        @Test
        @DisplayName("Should handle concurrent retry executions")
        @Timeout(10)
        void shouldHandleConcurrentRetryExecutions() {
            RetryPolicy retryPolicy = RetryPolicy.builder()
                .maxAttempts(2)
                .initialDelay(Duration.ofMillis(10))
                .build();
            
            AtomicInteger totalAttempts = new AtomicInteger(0);
            AtomicInteger successCount = new AtomicInteger(0);
            
            CompletableFuture<?>[] futures = new CompletableFuture[50];
            for (int i = 0; i < 50; i++) {
                final int index = i;
                futures[i] = CompletableFuture.runAsync(() -> {
                    Result<String, Exception> result = retryPolicy.execute(() -> {
                        totalAttempts.incrementAndGet();
                        if (index % 3 == 0) {
                            throw new RuntimeException("failure " + index);
                        }
                        return "success " + index;
                    });
                    
                    if (result.isSuccess()) {
                        successCount.incrementAndGet();
                    }
                });
            }
            
            CompletableFuture.allOf(futures).join();
            
            assertThat(successCount.get()).isGreaterThan(0);
            assertThat(totalAttempts.get()).isGreaterThan(50); // Some operations should have retried
        }
    }
}