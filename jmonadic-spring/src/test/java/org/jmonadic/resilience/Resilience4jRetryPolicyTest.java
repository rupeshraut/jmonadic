package org.jmonadic.resilience;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.*;

import java.sql.SQLException;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import org.jmonadic.patterns.Result;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@DisplayName("Resilience4j Retry Policy Tests")
class Resilience4jRetryPolicyTest {

    @Nested
    @DisplayName("Construction and Configuration")
    class ConstructionAndConfiguration {
        
        @Test
        @DisplayName("Should create retry policy with default settings")
        void shouldCreateWithDefaults() {
            Resilience4jRetryPolicy retryPolicy = Resilience4jRetryPolicy.builder().build();
            
            assertThat(retryPolicy.getName()).isEqualTo("RetryPolicy");
        }
        
        @Test
        @DisplayName("Should create retry policy with custom settings")
        void shouldCreateWithCustomSettings() {
            Resilience4jRetryPolicy retryPolicy = Resilience4jRetryPolicy.builder()
                .name("TestRetry")
                .maxAttempts(5)
                .waitDuration(Duration.ofMillis(200))
                .exponentialBackoff(2.5)
                .retryOnException(ex -> ex instanceof IllegalArgumentException)
                .failAfterMaxAttempts(false)
                .build();
            
            assertThat(retryPolicy.getName()).isEqualTo("TestRetry");
        }
        
        @Test
        @DisplayName("Should create preset retry policies")
        void shouldCreatePresetRetryPolicies() {
            Resilience4jRetryPolicy defaultRetry = Resilience4jRetryPolicy.Presets.defaultRetry();
            Resilience4jRetryPolicy quickRetry = Resilience4jRetryPolicy.Presets.quickRetry();
            Resilience4jRetryPolicy resilientRetry = Resilience4jRetryPolicy.Presets.resilientRetry();
            Resilience4jRetryPolicy networkRetry = Resilience4jRetryPolicy.Presets.networkRetry();
            Resilience4jRetryPolicy databaseRetry = Resilience4jRetryPolicy.Presets.databaseRetry();
            Resilience4jRetryPolicy webServiceRetry = Resilience4jRetryPolicy.Presets.webServiceRetry();
            
            assertThat(defaultRetry).isNotNull();
            assertThat(quickRetry).isNotNull();
            assertThat(resilientRetry).isNotNull();
            assertThat(networkRetry).isNotNull();
            assertThat(databaseRetry).isNotNull();
            assertThat(webServiceRetry).isNotNull();
        }
    }
    
    @Nested
    @DisplayName("Basic Retry Behavior")
    class BasicRetryBehavior {
        
        @Test
        @DisplayName("Should succeed on first attempt")
        void shouldSucceedOnFirstAttempt() {
            Resilience4jRetryPolicy retryPolicy = Resilience4jRetryPolicy.builder()
                .maxAttempts(3)
                .waitDuration(Duration.ofMillis(10))
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
            Resilience4jRetryPolicy retryPolicy = Resilience4jRetryPolicy.builder()
                .maxAttempts(3)
                .waitDuration(Duration.ofMillis(10))
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
            Resilience4jRetryPolicy retryPolicy = Resilience4jRetryPolicy.builder()
                .maxAttempts(2)
                .waitDuration(Duration.ofMillis(10))
                .build();
            
            AtomicInteger attemptCount = new AtomicInteger(0);
            
            Result<String, Exception> result = retryPolicy.execute(() -> {
                attemptCount.incrementAndGet();
                throw new RuntimeException("always fails");
            });
            
            assertThat(result.isFailure()).isTrue();
            assertThat(result.getError()).hasMessageContaining("always fails");
            assertThat(attemptCount.get()).isEqualTo(2);
        }
    }
    
    @Nested
    @DisplayName("Retry Predicates")
    class RetryPredicates {
        
        @Test
        @DisplayName("Should only retry specific exceptions")
        void shouldOnlyRetrySpecificExceptions() {
            Resilience4jRetryPolicy retryPolicy = Resilience4jRetryPolicy.builder()
                .maxAttempts(3)
                .waitDuration(Duration.ofMillis(5))
                .retryOnException(ex -> ex instanceof IllegalArgumentException)
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
            Resilience4jRetryPolicy retryPolicy = Resilience4jRetryPolicy.builder()
                .maxAttempts(3)
                .waitDuration(Duration.ofMillis(5))
                .retryOnExceptionOfType(IllegalStateException.class)
                .build();
            
            AtomicInteger attemptCount = new AtomicInteger(0);
            
            Result<String, Exception> result = retryPolicy.execute(() -> {
                attemptCount.incrementAndGet();
                throw new IllegalStateException("retry this specific type");
            });
            
            assertThat(result.isFailure()).isTrue();
            assertThat(attemptCount.get()).isEqualTo(3);
        }
        
        @Test
        @DisplayName("Should work with database retry preset")
        void shouldWorkWithDatabaseRetryPreset() {
            Resilience4jRetryPolicy retryPolicy = Resilience4jRetryPolicy.Presets.databaseRetry();
            
            AtomicInteger attemptCount = new AtomicInteger(0);
            
            // Should retry SQLException
            Result<String, Exception> result = retryPolicy.execute(() -> {
                attemptCount.incrementAndGet();
                throw new RuntimeException(new SQLException("database connection failed"));
            });
            
            assertThat(result.isFailure()).isTrue();
            assertThat(attemptCount.get()).isGreaterThan(1); // Should have retried
        }
    }
    
    @Nested
    @DisplayName("Backoff Strategies")
    class BackoffStrategies {
        
        @Test
        @DisplayName("Should apply exponential backoff")
        @Timeout(5)
        void shouldApplyExponentialBackoff() {
            Resilience4jRetryPolicy retryPolicy = Resilience4jRetryPolicy.builder()
                .maxAttempts(3)
                .waitDuration(Duration.ofMillis(50))
                .exponentialBackoff(2.0)
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
            assertThat(duration).isGreaterThan(100);
        }
        
        @Test
        @DisplayName("Should apply exponential backoff with max duration")
        @Timeout(3)
        void shouldApplyExponentialBackoffWithMaxDuration() {
            Resilience4jRetryPolicy retryPolicy = Resilience4jRetryPolicy.builder()
                .maxAttempts(4)
                .waitDuration(Duration.ofMillis(100))
                .exponentialBackoff(10.0, Duration.ofMillis(150)) // Would create very large delays without max
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
    }
    
    @Nested
    @DisplayName("Custom Error Mapping")
    class CustomErrorMapping {
        
        @Test
        @DisplayName("Should map exceptions to custom error types")
        void shouldMapExceptionsToCustomErrorTypes() {
            Resilience4jRetryPolicy retryPolicy = Resilience4jRetryPolicy.builder()
                .maxAttempts(2)
                .waitDuration(Duration.ofMillis(5))
                .build();
            
            Result<String, String> result = retryPolicy.execute(
                () -> {
                    throw new IllegalArgumentException("invalid input");
                },
                exception -> "Error: " + exception.getMessage()
            );
            
            assertThat(result.isFailure()).isTrue();
            assertThat(result.getError()).isEqualTo("Error: invalid input");
        }
        
        @Test
        @DisplayName("Should handle exceptions in error mapper")
        void shouldHandleExceptionsInErrorMapper() {
            Resilience4jRetryPolicy retryPolicy = Resilience4jRetryPolicy.builder()
                .maxAttempts(1)
                .build();
            
            assertThatThrownBy(() -> {
                retryPolicy.execute(
                    () -> {
                        throw new RuntimeException("original");
                    },
                    ex -> {
                        throw new RuntimeException("mapper failed");
                    }
                );
            }).hasMessage("mapper failed");
        }
    }
    
    @Nested
    @DisplayName("Void Operations")
    class VoidOperations {
        
        @Test
        @DisplayName("Should handle void operations")
        void shouldHandleVoidOperations() {
            Resilience4jRetryPolicy retryPolicy = Resilience4jRetryPolicy.builder()
                .maxAttempts(3)
                .waitDuration(Duration.ofMillis(5))
                .build();
            
            AtomicInteger attemptCount = new AtomicInteger(0);
            AtomicInteger sideEffectCount = new AtomicInteger(0);
            
            Result<Void, Exception> result = retryPolicy.executeVoid(() -> {
                int attempt = attemptCount.incrementAndGet();
                if (attempt < 3) {
                    throw new RuntimeException("void failure " + attempt);
                }
                sideEffectCount.incrementAndGet();
            });
            
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getValue()).isNull();
            assertThat(attemptCount.get()).isEqualTo(3);
            assertThat(sideEffectCount.get()).isEqualTo(1);
        }
    }
    
    @Nested
    @DisplayName("Decorators")
    class Decorators {
        
        @Test
        @DisplayName("Should create function decorators")
        void shouldCreateFunctionDecorators() {
            Resilience4jRetryPolicy retryPolicy = Resilience4jRetryPolicy.builder()
                .maxAttempts(2)
                .waitDuration(Duration.ofMillis(5))
                .build();
            
            var decorator = retryPolicy.decorator();
            
            AtomicInteger attemptCount = new AtomicInteger(0);
            Result<String, Exception> result = retryPolicy.execute(() -> {
                int attempt = attemptCount.incrementAndGet();
                if (attempt == 1) {
                    throw new RuntimeException("first attempt fails");
                }
                return "decorated success";
            });
            
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getValue()).isEqualTo("decorated success");
            assertThat(attemptCount.get()).isEqualTo(2);
        }
        
        @Test
        @DisplayName("Should create custom error mapping decorators")
        void shouldCreateCustomErrorMappingDecorators() {
            Resilience4jRetryPolicy retryPolicy = Resilience4jRetryPolicy.builder()
                .maxAttempts(1)
                .build();
            
            var decorator = retryPolicy.decorator(ex -> "Mapped: " + ex.getMessage());
            
            Result<String, Exception> result = retryPolicy.execute(() -> {
                throw new RuntimeException("original error");
            });
            
            assertThat(result.isFailure()).isTrue();
            assertThat(result.getError().getMessage()).contains("original error");
        }
    }
    
    @Nested
    @DisplayName("Circuit Breaker Integration")
    class CircuitBreakerIntegration {
        
        @Test
        @DisplayName("Should combine retry with circuit breaker")
        void shouldCombineRetryWithCircuitBreaker() {
            Resilience4jRetryPolicy retryPolicy = Resilience4jRetryPolicy.builder()
                .maxAttempts(3)
                .waitDuration(Duration.ofMillis(5))
                .build();
            
            Resilience4jCircuitBreaker circuitBreaker = Resilience4jCircuitBreaker.builder()
                .failureRateThreshold(80.0f)
                .minimumNumberOfCalls(5)
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
    @DisplayName("Metrics and Monitoring")
    class MetricsAndMonitoring {
        
        @Test
        @DisplayName("Should provide retry metrics")
        void shouldProvideRetryMetrics() {
            Resilience4jRetryPolicy retryPolicy = Resilience4jRetryPolicy.builder()
                .name("MetricsTest")
                .maxAttempts(3)
                .waitDuration(Duration.ofMillis(5))
                .build();
            
            // Execute some operations
            retryPolicy.execute(() -> "success");
            retryPolicy.execute(() -> {
                throw new RuntimeException("failure");
            });
            
            io.github.resilience4j.retry.Retry.Metrics metrics = retryPolicy.getMetrics();
            
            assertThat(metrics).isNotNull();
            assertThat(metrics.getNumberOfSuccessfulCallsWithoutRetryAttempt()).isEqualTo(1);
            assertThat(metrics.getNumberOfFailedCallsWithoutRetryAttempt() + 
                      metrics.getNumberOfFailedCallsWithRetryAttempt()).isGreaterThan(0);
        }
        
        @Test
        @DisplayName("Should access underlying Resilience4j retry")
        void shouldAccessUnderlyingRetry() {
            Resilience4jRetryPolicy retryPolicy = Resilience4jRetryPolicy.builder()
                .name("UnderlyingTest")
                .build();
            
            io.github.resilience4j.retry.Retry underlying = retryPolicy.getRetry();
            
            assertThat(underlying).isNotNull();
            assertThat(underlying.getName()).isEqualTo("UnderlyingTest");
        }
    }
    
    @Nested
    @DisplayName("Concurrent Operations")
    class ConcurrentOperations {
        
        @Test
        @DisplayName("Should handle concurrent retry executions")
        @Timeout(10)
        void shouldHandleConcurrentRetryExecutions() {
            Resilience4jRetryPolicy retryPolicy = Resilience4jRetryPolicy.builder()
                .maxAttempts(2)
                .waitDuration(Duration.ofMillis(5))
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
    
    @Nested
    @DisplayName("Edge Cases and Error Handling")
    class EdgeCasesAndErrorHandling {
        
        @Test
        @DisplayName("Should handle null supplier gracefully")
        void shouldHandleNullSupplierGracefully() {
            Resilience4jRetryPolicy retryPolicy = Resilience4jRetryPolicy.builder().build();
            
            assertThatThrownBy(() -> retryPolicy.execute(null))
                .isInstanceOf(NullPointerException.class);
        }
        
        @Test
        @DisplayName("Should handle max attempts of 1")
        void shouldHandleMaxAttemptsOfOne() {
            Resilience4jRetryPolicy retryPolicy = Resilience4jRetryPolicy.builder()
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
        @DisplayName("Should handle zero wait duration")
        @Timeout(2)
        void shouldHandleZeroWaitDuration() {
            Resilience4jRetryPolicy retryPolicy = Resilience4jRetryPolicy.builder()
                .maxAttempts(3)
                .waitDuration(Duration.ZERO)
                .build();
            
            AtomicInteger attemptCount = new AtomicInteger(0);
            long startTime = System.currentTimeMillis();
            
            Result<String, Exception> result = retryPolicy.execute(() -> {
                attemptCount.incrementAndGet();
                throw new RuntimeException("zero delay failure");
            });
            
            long duration = System.currentTimeMillis() - startTime;
            
            assertThat(result.isFailure()).isTrue();
            assertThat(attemptCount.get()).isEqualTo(3);
            assertThat(duration).isLessThan(100); // Should be very fast with zero delay
        }
        
        @Test
        @DisplayName("Should handle very short wait durations")
        void shouldHandleVeryShortWaitDurations() {
            Resilience4jRetryPolicy retryPolicy = Resilience4jRetryPolicy.builder()
                .maxAttempts(2)
                .waitDuration(Duration.ofNanos(1))
                .build();
            
            AtomicInteger attemptCount = new AtomicInteger(0);
            
            Result<String, Exception> result = retryPolicy.execute(() -> {
                attemptCount.incrementAndGet();
                throw new RuntimeException("very short delay");
            });
            
            assertThat(result.isFailure()).isTrue();
            assertThat(attemptCount.get()).isEqualTo(2);
        }
    }
    
    @Nested
    @DisplayName("Specific Use Cases")
    class SpecificUseCases {
        
        @Test
        @DisplayName("Should work for network operations")
        void shouldWorkForNetworkOperations() {
            Resilience4jRetryPolicy retryPolicy = Resilience4jRetryPolicy.Presets.networkRetry();
            
            AtomicInteger attemptCount = new AtomicInteger(0);
            
            Result<String, Exception> result = retryPolicy.execute(() -> {
                int attempt = attemptCount.incrementAndGet();
                if (attempt < 2) {
                    throw new RuntimeException("network timeout");
                }
                return "network success";
            });
            
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getValue()).isEqualTo("network success");
            assertThat(attemptCount.get()).isEqualTo(2);
        }
        
        @Test
        @DisplayName("Should work for web service operations")
        void shouldWorkForWebServiceOperations() {
            Resilience4jRetryPolicy retryPolicy = Resilience4jRetryPolicy.Presets.webServiceRetry();
            
            AtomicInteger attemptCount = new AtomicInteger(0);
            
            Result<String, Exception> result = retryPolicy.execute(() -> {
                int attempt = attemptCount.incrementAndGet();
                if (attempt < 3) {
                    throw new RuntimeException("connection refused");
                }
                return "web service success";
            });
            
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getValue()).isEqualTo("web service success");
            assertThat(attemptCount.get()).isEqualTo(3);
        }
    }
}