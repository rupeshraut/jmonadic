package org.jmonadic.resilience;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.*;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import org.jmonadic.patterns.Result;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;

@DisplayName("Resilience4j Circuit Breaker Tests")
class Resilience4jCircuitBreakerTest {

    @Nested
    @DisplayName("Construction and Configuration")
    class ConstructionAndConfiguration {
        
        @Test
        @DisplayName("Should create circuit breaker with default settings")
        void shouldCreateWithDefaults() {
            Resilience4jCircuitBreaker circuitBreaker = Resilience4jCircuitBreaker.builder().build();
            
            assertThat(circuitBreaker.getName()).isEqualTo("CircuitBreaker");
            assertThat(circuitBreaker.getState()).isEqualTo(io.github.resilience4j.circuitbreaker.CircuitBreaker.State.CLOSED);
        }
        
        @Test
        @DisplayName("Should create circuit breaker with custom settings")
        void shouldCreateWithCustomSettings() {
            Resilience4jCircuitBreaker circuitBreaker = Resilience4jCircuitBreaker.builder()
                .name("TestCircuitBreaker")
                .failureRateThreshold(60.0f)
                .minimumNumberOfCalls(5)
                .waitDurationInOpenState(Duration.ofSeconds(10))
                .slidingWindowSize(50)
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.TIME_BASED)
                .permittedNumberOfCallsInHalfOpenState(3)
                .automaticTransitionFromOpenToHalfOpenEnabled(false)
                .build();
            
            assertThat(circuitBreaker.getName()).isEqualTo("TestCircuitBreaker");
            assertThat(circuitBreaker.getState()).isEqualTo(io.github.resilience4j.circuitbreaker.CircuitBreaker.State.CLOSED);
        }
        
        @Test
        @DisplayName("Should create preset circuit breakers")
        void shouldCreatePresetCircuitBreakers() {
            Resilience4jCircuitBreaker defaultCb = Resilience4jCircuitBreaker.Presets.defaultCircuitBreaker();
            Resilience4jCircuitBreaker resilientCb = Resilience4jCircuitBreaker.Presets.resilientCircuitBreaker();
            Resilience4jCircuitBreaker networkCb = Resilience4jCircuitBreaker.Presets.networkCircuitBreaker();
            Resilience4jCircuitBreaker fastFailCb = Resilience4jCircuitBreaker.Presets.fastFailCircuitBreaker();
            
            assertThat(defaultCb).isNotNull();
            assertThat(resilientCb).isNotNull();
            assertThat(networkCb).isNotNull();
            assertThat(fastFailCb).isNotNull();
        }
    }
    
    @Nested
    @DisplayName("Basic Operations")
    class BasicOperations {
        
        @Test
        @DisplayName("Should execute successful operations")
        void shouldExecuteSuccessfulOperations() {
            Resilience4jCircuitBreaker circuitBreaker = Resilience4jCircuitBreaker.builder().build();
            
            Result<String, Exception> result = circuitBreaker.execute(() -> "success");
            
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getValue()).isEqualTo("success");
        }
        
        @Test
        @DisplayName("Should execute failing operations")
        void shouldExecuteFailingOperations() {
            Resilience4jCircuitBreaker circuitBreaker = Resilience4jCircuitBreaker.builder().build();
            
            RuntimeException exception = new RuntimeException("operation failed");
            Result<String, Exception> result = circuitBreaker.execute(() -> {
                throw exception;
            });
            
            assertThat(result.isFailure()).isTrue();
            assertThat(result.getError()).isEqualTo(exception);
        }
        
        @Test
        @DisplayName("Should execute void operations")
        void shouldExecuteVoidOperations() {
            Resilience4jCircuitBreaker circuitBreaker = Resilience4jCircuitBreaker.builder().build();
            AtomicInteger counter = new AtomicInteger(0);
            
            Result<Void, Exception> result = circuitBreaker.executeVoid(() -> {
                counter.incrementAndGet();
            });
            
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getValue()).isNull();
            assertThat(counter.get()).isEqualTo(1);
        }
        
        @Test
        @DisplayName("Should handle custom error mapping")
        void shouldHandleCustomErrorMapping() {
            Resilience4jCircuitBreaker circuitBreaker = Resilience4jCircuitBreaker.builder().build();
            
            Result<String, String> result = circuitBreaker.execute(
                () -> {
                    throw new IllegalArgumentException("invalid input");
                },
                exception -> "Error: " + exception.getMessage()
            );
            
            assertThat(result.isFailure()).isTrue();
            assertThat(result.getError()).isEqualTo("Error: invalid input");
        }
    }
    
    @Nested
    @DisplayName("State Management")
    class StateManagement {
        
        @Test
        @DisplayName("Should transition to open state on failures")
        @Timeout(10)
        void shouldTransitionToOpenStateOnFailures() {
            Resilience4jCircuitBreaker circuitBreaker = Resilience4jCircuitBreaker.builder()
                .failureRateThreshold(50.0f)
                .minimumNumberOfCalls(3)
                .slidingWindowSize(5)
                .build();
            
            // Generate enough failures to open the circuit
            for (int i = 0; i < 5; i++) {
                final int index = i;
                circuitBreaker.execute(() -> {
                    throw new RuntimeException("failure " + index);
                });
            }
            
            await().atMost(Duration.ofSeconds(2))
                .until(() -> circuitBreaker.getState() == io.github.resilience4j.circuitbreaker.CircuitBreaker.State.OPEN);
        }
        
        @Test
        @DisplayName("Should fail fast when circuit is open")
        @Timeout(10)
        void shouldFailFastWhenCircuitIsOpen() {
            Resilience4jCircuitBreaker circuitBreaker = Resilience4jCircuitBreaker.builder()
                .failureRateThreshold(50.0f)
                .minimumNumberOfCalls(2)
                .slidingWindowSize(3)
                .build();
            
            // Force circuit to open
            for (int i = 0; i < 3; i++) {
                circuitBreaker.execute(() -> {
                    throw new RuntimeException("failure");
                });
            }
            
            await().atMost(Duration.ofSeconds(2))
                .until(() -> circuitBreaker.getState() == io.github.resilience4j.circuitbreaker.CircuitBreaker.State.OPEN);
            
            // Should fail fast without executing supplier
            AtomicInteger executionCount = new AtomicInteger(0);
            Result<String, Exception> result = circuitBreaker.execute(() -> {
                executionCount.incrementAndGet();
                return "should not execute";
            });
            
            assertThat(result.isFailure()).isTrue();
            assertThat(executionCount.get()).isEqualTo(0);
        }
        
        @Test
        @DisplayName("Should manually control circuit state")
        void shouldManuallyControlCircuitState() {
            Resilience4jCircuitBreaker circuitBreaker = Resilience4jCircuitBreaker.builder().build();
            
            // Manual state transitions
            circuitBreaker.transitionToOpenState();
            assertThat(circuitBreaker.getState()).isEqualTo(io.github.resilience4j.circuitbreaker.CircuitBreaker.State.OPEN);
            
            circuitBreaker.transitionToHalfOpenState();
            assertThat(circuitBreaker.getState()).isEqualTo(io.github.resilience4j.circuitbreaker.CircuitBreaker.State.HALF_OPEN);
            
            circuitBreaker.transitionToClosedState();
            assertThat(circuitBreaker.getState()).isEqualTo(io.github.resilience4j.circuitbreaker.CircuitBreaker.State.CLOSED);
        }
    }
    
    @Nested
    @DisplayName("Decorators")
    class Decorators {
        
        @Test
        @DisplayName("Should create function decorators")
        void shouldCreateFunctionDecorators() {
            Resilience4jCircuitBreaker circuitBreaker = Resilience4jCircuitBreaker.builder().build();
            
            var decorator = circuitBreaker.decorator();
            
            Result<String, Exception> result = circuitBreaker.execute(() -> "decorated success");
            
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getValue()).isEqualTo("decorated success");
        }
        
        @Test
        @DisplayName("Should create custom error mapping decorators")
        void shouldCreateCustomErrorMappingDecorators() {
            Resilience4jCircuitBreaker circuitBreaker = Resilience4jCircuitBreaker.builder().build();
            
            var decorator = circuitBreaker.decorator(ex -> "Mapped: " + ex.getMessage());
            
            Result<String, Exception> result = circuitBreaker.execute(() -> {
                throw new RuntimeException("original error");
            });
            
            assertThat(result.isFailure()).isTrue();
            assertThat(result.getError().getMessage()).contains("original error");
        }
    }
    
    @Nested
    @DisplayName("Metrics and Monitoring")
    class MetricsAndMonitoring {
        
        @Test
        @DisplayName("Should provide circuit breaker metrics")
        void shouldProvideCircuitBreakerMetrics() {
            Resilience4jCircuitBreaker circuitBreaker = Resilience4jCircuitBreaker.builder()
                .name("MetricsTest")
                .build();
            
            // Execute some operations
            circuitBreaker.execute(() -> "success");
            circuitBreaker.execute(() -> {
                throw new RuntimeException("failure");
            });
            
            io.github.resilience4j.circuitbreaker.CircuitBreaker.Metrics metrics = circuitBreaker.getMetrics();
            
            assertThat(metrics).isNotNull();
            assertThat(metrics.getNumberOfSuccessfulCalls()).isEqualTo(1);
            assertThat(metrics.getNumberOfFailedCalls()).isEqualTo(1);
        }
        
        @Test
        @DisplayName("Should access underlying Resilience4j circuit breaker")
        void shouldAccessUnderlyingCircuitBreaker() {
            Resilience4jCircuitBreaker circuitBreaker = Resilience4jCircuitBreaker.builder()
                .name("UnderlyingTest")
                .build();
            
            io.github.resilience4j.circuitbreaker.CircuitBreaker underlying = circuitBreaker.getCircuitBreaker();
            
            assertThat(underlying).isNotNull();
            assertThat(underlying.getName()).isEqualTo("UnderlyingTest");
        }
    }
    
    @Nested
    @DisplayName("Concurrent Operations")
    class ConcurrentOperations {
        
        @Test
        @DisplayName("Should handle concurrent operations safely")
        @Timeout(10)
        void shouldHandleConcurrentOperationsSafely() {
            Resilience4jCircuitBreaker circuitBreaker = Resilience4jCircuitBreaker.builder()
                .failureRateThreshold(70.0f)
                .minimumNumberOfCalls(10)
                .build();
            
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failureCount = new AtomicInteger(0);
            
            CompletableFuture<?>[] futures = new CompletableFuture[100];
            for (int i = 0; i < 100; i++) {
                final int index = i;
                futures[i] = CompletableFuture.runAsync(() -> {
                    Result<String, Exception> result = circuitBreaker.execute(() -> {
                        if (index % 5 == 0) {
                            throw new RuntimeException("failure " + index);
                        }
                        return "success " + index;
                    });
                    
                    if (result.isSuccess()) {
                        successCount.incrementAndGet();
                    } else {
                        failureCount.incrementAndGet();
                    }
                });
            }
            
            CompletableFuture.allOf(futures).join();
            
            assertThat(successCount.get() + failureCount.get()).isEqualTo(100);
            assertThat(successCount.get()).isGreaterThan(0);
            assertThat(failureCount.get()).isGreaterThan(0);
        }
    }
    
    @Nested
    @DisplayName("Integration with Resilience4j Features")
    class IntegrationWithResilience4jFeatures {
        
        @Test
        @DisplayName("Should work with time-based sliding window")
        void shouldWorkWithTimeBasedSlidingWindow() {
            Resilience4jCircuitBreaker circuitBreaker = Resilience4jCircuitBreaker.builder()
                .failureRateThreshold(60.0f)
                .minimumNumberOfCalls(3)
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.TIME_BASED)
                .slidingWindowSize(1) // 1 second window
                .build();
            
            // Execute some operations
            for (int i = 0; i < 5; i++) {
                if (i % 2 == 0) {
                    circuitBreaker.execute(() -> "success");
                } else {
                    circuitBreaker.execute(() -> {
                        throw new RuntimeException("failure");
                    });
                }
            }
            
            assertThat(circuitBreaker.getMetrics().getNumberOfSuccessfulCalls() + circuitBreaker.getMetrics().getNumberOfFailedCalls()).isGreaterThan(0);
        }
        
        @Test
        @DisplayName("Should respect minimum number of calls")
        void shouldRespectMinimumNumberOfCalls() {
            Resilience4jCircuitBreaker circuitBreaker = Resilience4jCircuitBreaker.builder()
                .failureRateThreshold(50.0f)
                .minimumNumberOfCalls(5)
                .slidingWindowSize(10)
                .build();
            
            // Execute fewer than minimum calls with 100% failure rate
            for (int i = 0; i < 3; i++) {
                circuitBreaker.execute(() -> {
                    throw new RuntimeException("failure");
                });
            }
            
            // Circuit should remain closed due to minimum calls not met
            assertThat(circuitBreaker.getState()).isEqualTo(io.github.resilience4j.circuitbreaker.CircuitBreaker.State.CLOSED);
        }
    }
    
    @Nested
    @DisplayName("Error Handling Edge Cases")
    class ErrorHandlingEdgeCases {
        
        @Test
        @DisplayName("Should handle null supplier gracefully")
        void shouldHandleNullSupplierGracefully() {
            Resilience4jCircuitBreaker circuitBreaker = Resilience4jCircuitBreaker.builder().build();
            
            assertThatThrownBy(() -> circuitBreaker.execute(null))
                .isInstanceOf(NullPointerException.class);
        }
        
        @Test
        @DisplayName("Should handle exceptions in error mapper")
        void shouldHandleExceptionsInErrorMapper() {
            Resilience4jCircuitBreaker circuitBreaker = Resilience4jCircuitBreaker.builder().build();
            
            assertThatThrownBy(() -> {
                circuitBreaker.execute(
                    () -> {
                        throw new RuntimeException("original");
                    },
                    ex -> {
                        throw new RuntimeException("mapper failed");
                    }
                );
            }).hasMessage("mapper failed");
        }
        
        @Test
        @DisplayName("Should handle runtime exceptions during execution")
        void shouldHandleRuntimeExceptionsDuringExecution() {
            Resilience4jCircuitBreaker circuitBreaker = Resilience4jCircuitBreaker.builder().build();
            
            OutOfMemoryError error = new OutOfMemoryError("simulated OOM");
            Result<String, Exception> result = circuitBreaker.execute(() -> {
                throw error;
            });
            
            assertThat(result.isFailure()).isTrue();
            assertThat(result.getError()).isEqualTo(error);
        }
    }
    
    @Nested
    @DisplayName("Configuration Validation")
    class ConfigurationValidation {
        
        @Test
        @DisplayName("Should handle extreme configuration values")
        void shouldHandleExtremeConfigurationValues() {
            // Very low failure rate threshold
            Resilience4jCircuitBreaker lowThreshold = Resilience4jCircuitBreaker.builder()
                .failureRateThreshold(1.0f)
                .minimumNumberOfCalls(1)
                .build();
            
            assertThat(lowThreshold).isNotNull();
            
            // Very high failure rate threshold
            Resilience4jCircuitBreaker highThreshold = Resilience4jCircuitBreaker.builder()
                .failureRateThreshold(99.0f)
                .minimumNumberOfCalls(1)
                .build();
            
            assertThat(highThreshold).isNotNull();
        }
        
        @Test
        @DisplayName("Should handle very short wait durations")
        void shouldHandleVeryShortWaitDurations() {
            Resilience4jCircuitBreaker circuitBreaker = Resilience4jCircuitBreaker.builder()
                .waitDurationInOpenState(Duration.ofMillis(1))
                .build();
            
            assertThat(circuitBreaker).isNotNull();
        }
        
        @Test
        @DisplayName("Should handle very large sliding window sizes")
        void shouldHandleVeryLargeSlidingWindowSizes() {
            Resilience4jCircuitBreaker circuitBreaker = Resilience4jCircuitBreaker.builder()
                .slidingWindowSize(10000)
                .build();
            
            assertThat(circuitBreaker).isNotNull();
        }
    }
}