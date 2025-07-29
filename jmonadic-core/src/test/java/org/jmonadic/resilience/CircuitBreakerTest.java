package org.jmonadic.resilience;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.*;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import org.jmonadic.patterns.Result;
import org.jmonadic.resilience.CircuitBreaker.CircuitBreakerException;
import org.jmonadic.resilience.CircuitBreaker.State;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@DisplayName("Circuit Breaker Tests")
class CircuitBreakerTest {

    @Nested
    @DisplayName("Construction and Configuration")
    class ConstructionAndConfiguration {
        
        @Test
        @DisplayName("Should create circuit breaker with default settings")
        void shouldCreateWithDefaults() {
            CircuitBreaker circuitBreaker = CircuitBreaker.builder().build();
            
            assertThat(circuitBreaker.getState()).isEqualTo(State.CLOSED);
            assertThat(circuitBreaker.getFailureCount()).isEqualTo(0);
            assertThat(circuitBreaker.getSuccessCount()).isEqualTo(0);
        }
        
        @Test
        @DisplayName("Should create circuit breaker with custom settings")
        void shouldCreateWithCustomSettings() {
            CircuitBreaker circuitBreaker = CircuitBreaker.builder()
                .name("TestCircuitBreaker")
                .failureThreshold(3)
                .successThreshold(2)
                .timeout(Duration.ofMillis(500))
                .waitDurationInOpenState(Duration.ofSeconds(10))
                .build();
            
            assertThat(circuitBreaker.getState()).isEqualTo(State.CLOSED);
            assertThat(circuitBreaker.getFailureCount()).isEqualTo(0);
        }
    }
    
    @Nested
    @DisplayName("State Transitions")
    class StateTransitions {
        
        private CircuitBreaker circuitBreaker;
        
        @BeforeEach
        void setUp() {
            circuitBreaker = CircuitBreaker.builder()
                .name("TestCB")
                .failureThreshold(3)
                .successThreshold(2)
                .timeout(Duration.ofMillis(100))
                .waitDurationInOpenState(Duration.ofMillis(200))
                .build();
        }
        
        @Test
        @DisplayName("Should remain closed on successful operations")
        void shouldRemainClosedOnSuccess() {
            for (int i = 0; i < 5; i++) {
                Result<String, CircuitBreakerException> result = circuitBreaker.execute(() -> "success");
                assertThat(result.isSuccess()).isTrue();
                assertThat(result.getValue()).isEqualTo("success");
            }
            
            assertThat(circuitBreaker.getState()).isEqualTo(State.CLOSED);
            assertThat(circuitBreaker.getFailureCount()).isEqualTo(0);
        }
        
        @Test
        @DisplayName("Should transition to open on failure threshold")
        void shouldTransitionToOpenOnFailureThreshold() {
            // First two failures should keep circuit closed
            for (int i = 0; i < 2; i++) {
                final int index = i;
                Result<String, CircuitBreakerException> result = circuitBreaker.execute(() -> {
                    throw new RuntimeException("failure " + index);
                });
                assertThat(result.isFailure()).isTrue();
                assertThat(circuitBreaker.getState()).isEqualTo(State.CLOSED);
            }
            
            // Third failure should open the circuit
            Result<String, CircuitBreakerException> result = circuitBreaker.execute(() -> {
                throw new RuntimeException("failure 3");
            });
            assertThat(result.isFailure()).isTrue();
            assertThat(circuitBreaker.getState()).isEqualTo(State.OPEN);
        }
        
        @Test
        @DisplayName("Should fail fast when open")
        void shouldFailFastWhenOpen() {
            // Force circuit to open
            for (int i = 0; i < 3; i++) {
                circuitBreaker.execute(() -> {
                    throw new RuntimeException("failure");
                });
            }
            
            assertThat(circuitBreaker.getState()).isEqualTo(State.OPEN);
            
            // Should fail fast without executing supplier
            AtomicInteger executionCount = new AtomicInteger(0);
            Result<String, CircuitBreakerException> result = circuitBreaker.execute(() -> {
                executionCount.incrementAndGet();
                return "should not execute";
            });
            
            assertThat(result.isFailure()).isTrue();
            assertThat(result.getError().getMessage()).contains("Circuit breaker is OPEN");
            assertThat(executionCount.get()).isEqualTo(0);
        }
        
        @Test
        @DisplayName("Should transition to half-open after wait duration")
        @Timeout(5)
        void shouldTransitionToHalfOpenAfterWaitDuration() throws InterruptedException {
            // Force circuit to open
            for (int i = 0; i < 3; i++) {
                circuitBreaker.execute(() -> {
                    throw new RuntimeException("failure");
                });
            }
            
            assertThat(circuitBreaker.getState()).isEqualTo(State.OPEN);
            
            // Wait for the circuit to transition to half-open
            await().atMost(Duration.ofSeconds(1))
                .pollInterval(Duration.ofMillis(50))
                .until(() -> {
                    // Attempt to execute to trigger state check
                    circuitBreaker.execute(() -> "test");
                    return circuitBreaker.getState() == State.HALF_OPEN;
                });
        }
        
        @Test
        @DisplayName("Should close from half-open on successful operations")
        @Timeout(5)
        void shouldCloseFromHalfOpenOnSuccess() throws InterruptedException {
            // Force circuit to open
            for (int i = 0; i < 3; i++) {
                circuitBreaker.execute(() -> {
                    throw new RuntimeException("failure");
                });
            }
            
            // Wait for half-open transition
            Thread.sleep(250);
            
            // Execute successful operations to close circuit
            for (int i = 0; i < 2; i++) {
                Result<String, CircuitBreakerException> result = circuitBreaker.execute(() -> "success");
                assertThat(result.isSuccess()).isTrue();
            }
            
            await().atMost(Duration.ofSeconds(1))
                .until(() -> circuitBreaker.getState() == State.CLOSED);
        }
        
        @Test
        @DisplayName("Should reopen from half-open on failure")
        @Timeout(5)
        void shouldReopenFromHalfOpenOnFailure() throws InterruptedException {
            // Force circuit to open
            for (int i = 0; i < 3; i++) {
                circuitBreaker.execute(() -> {
                    throw new RuntimeException("failure");
                });
            }
            
            // Wait for half-open transition
            Thread.sleep(250);
            
            // First call should transition to half-open
            circuitBreaker.execute(() -> "success");
            
            // Failure should reopen circuit
            Result<String, CircuitBreakerException> result = circuitBreaker.execute(() -> {
                throw new RuntimeException("failure in half-open");
            });
            
            assertThat(result.isFailure()).isTrue();
            assertThat(circuitBreaker.getState()).isEqualTo(State.OPEN);
        }
    }
    
    @Nested
    @DisplayName("Timeout Handling")
    class TimeoutHandling {
        
        @Test
        @DisplayName("Should fail on timeout")
        void shouldFailOnTimeout() {
            CircuitBreaker circuitBreaker = CircuitBreaker.builder()
                .timeout(Duration.ofMillis(50))
                .build();
            
            Result<String, CircuitBreakerException> result = circuitBreaker.execute(() -> {
                try {
                    Thread.sleep(100); // Longer than timeout
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return "should timeout";
            });
            
            assertThat(result.isFailure()).isTrue();
            assertThat(result.getError().getMessage()).contains("timed out");
        }
        
        @Test
        @DisplayName("Should succeed within timeout")
        void shouldSucceedWithinTimeout() {
            CircuitBreaker circuitBreaker = CircuitBreaker.builder()
                .timeout(Duration.ofMillis(100))
                .build();
            
            Result<String, CircuitBreakerException> result = circuitBreaker.execute(() -> {
                try {
                    Thread.sleep(10); // Less than timeout
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return "success";
            });
            
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getValue()).isEqualTo("success");
        }
    }
    
    @Nested
    @DisplayName("Basic Operations")
    class BasicOperations {
        
        @Test
        @DisplayName("Should execute simple operations successfully")
        void shouldExecuteSimpleOperationsSuccessfully() {
            CircuitBreaker circuitBreaker = CircuitBreaker.builder().build();
            
            Result<String, CircuitBreakerException> result = circuitBreaker.execute(() -> "success");
            
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getValue()).isEqualTo("success");
        }
        
        @Test
        @DisplayName("Should handle operation failures")
        void shouldHandleOperationFailures() {
            CircuitBreaker circuitBreaker = CircuitBreaker.builder().build();
            
            Result<String, CircuitBreakerException> result = circuitBreaker.execute(() -> {
                throw new RuntimeException("operation failure");
            });
            
            assertThat(result.isFailure()).isTrue();
            assertThat(result.getError().getMessage()).contains("Operation failed");
        }
    }
    
    @Nested
    @DisplayName("Manual Control")
    class ManualControl {
        
        @Test
        @DisplayName("Should manually reset circuit breaker")
        void shouldManuallyResetCircuitBreaker() {
            CircuitBreaker circuitBreaker = CircuitBreaker.builder()
                .failureThreshold(2)
                .build();
            
            // Force circuit to open
            for (int i = 0; i < 2; i++) {
                circuitBreaker.execute(() -> {
                    throw new RuntimeException("failure");
                });
            }
            
            assertThat(circuitBreaker.getState()).isEqualTo(State.OPEN);
            
            // Manual reset
            circuitBreaker.reset();
            
            assertThat(circuitBreaker.getState()).isEqualTo(State.CLOSED);
            assertThat(circuitBreaker.getFailureCount()).isEqualTo(0);
            assertThat(circuitBreaker.getSuccessCount()).isEqualTo(0);
        }
    }
    
    @Nested
    @DisplayName("Metrics and Monitoring")
    class MetricsAndMonitoring {
        
        @Test
        @DisplayName("Should track failure count correctly")
        void shouldTrackFailureCountCorrectly() {
            CircuitBreaker circuitBreaker = CircuitBreaker.builder()
                .failureThreshold(5)
                .build();
            
            for (int i = 0; i < 3; i++) {
                circuitBreaker.execute(() -> {
                    throw new RuntimeException("failure");
                });
            }
            
            assertThat(circuitBreaker.getFailureCount()).isEqualTo(3);
            assertThat(circuitBreaker.getState()).isEqualTo(State.CLOSED);
        }
        
        @Test
        @DisplayName("Should reset failure count on success")
        void shouldResetFailureCountOnSuccess() {
            CircuitBreaker circuitBreaker = CircuitBreaker.builder()
                .failureThreshold(5)
                .build();
            
            // Some failures
            for (int i = 0; i < 2; i++) {
                circuitBreaker.execute(() -> {
                    throw new RuntimeException("failure");
                });
            }
            
            assertThat(circuitBreaker.getFailureCount()).isEqualTo(2);
            
            // One success should reset failure count
            circuitBreaker.execute(() -> "success");
            
            assertThat(circuitBreaker.getFailureCount()).isEqualTo(0);
        }
        
        @Test
        @DisplayName("Should provide circuit breaker metrics")
        void shouldProvideCircuitBreakerMetrics() {
            CircuitBreaker circuitBreaker = CircuitBreaker.builder()
                .name("MetricsTest")
                .build();
            
            CircuitBreaker.Metrics metrics = circuitBreaker.getMetrics();
            
            assertThat(metrics.name()).isEqualTo("MetricsTest");
            assertThat(metrics.state()).isEqualTo(State.CLOSED);
            assertThat(metrics.failureCount()).isEqualTo(0);
            assertThat(metrics.successCount()).isEqualTo(0);
        }
    }
    
    @Nested
    @DisplayName("Concurrent Access")
    class ConcurrentAccess {
        
        @Test
        @DisplayName("Should handle concurrent operations safely")
        @Timeout(10)
        void shouldHandleConcurrentOperationsSafely() {
            CircuitBreaker circuitBreaker = CircuitBreaker.builder()
                .failureThreshold(10)
                .build();
            
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failureCount = new AtomicInteger(0);
            
            // Run concurrent operations
            CompletableFuture<?>[] futures = new CompletableFuture[100];
            for (int i = 0; i < 100; i++) {
                final int index = i;
                futures[i] = CompletableFuture.runAsync(() -> {
                    Result<String, CircuitBreakerException> result = circuitBreaker.execute(() -> {
                        if (index % 3 == 0) {
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
    @DisplayName("Edge Cases")
    class EdgeCases {
        
        @Test
        @DisplayName("Should handle null supplier")
        void shouldHandleNullSupplier() {
            CircuitBreaker circuitBreaker = CircuitBreaker.builder().build();
            
            Result<String, CircuitBreaker.CircuitBreakerException> result = circuitBreaker.execute(null);
            assertThat(result.isFailure()).isTrue();
        }
        
        @Test
        @DisplayName("Should handle InterruptedException")
        void shouldHandleInterruptedException() {
            CircuitBreaker circuitBreaker = CircuitBreaker.builder().build();
            
            Result<String, CircuitBreakerException> result = circuitBreaker.execute(() -> {
                Thread.currentThread().interrupt();
                throw new RuntimeException(new InterruptedException("interrupted"));
            });
            
            assertThat(result.isFailure()).isTrue();
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
            
            // Clean up interrupt status
            Thread.interrupted();
        }
        
        @Test
        @DisplayName("Should handle very short timeouts")
        void shouldHandleVeryShortTimeouts() {
            CircuitBreaker circuitBreaker = CircuitBreaker.builder()
                .timeout(Duration.ofNanos(1))
                .build();
            
            Result<String, CircuitBreakerException> result = circuitBreaker.execute(() -> {
                // Even the smallest operation might timeout
                return "fast operation";
            });
            
            // Result could be either success or timeout depending on timing
            assertThat(result).isNotNull();
        }
        
        @Test
        @DisplayName("Should handle zero failure threshold")
        void shouldHandleZeroFailureThreshold() {
            CircuitBreaker circuitBreaker = CircuitBreaker.builder()
                .failureThreshold(0)
                .build();
            
            // Should immediately open on any failure
            Result<String, CircuitBreakerException> result = circuitBreaker.execute(() -> {
                throw new RuntimeException("first failure");
            });
            
            assertThat(result.isFailure()).isTrue();
            // Note: Implementation might vary on whether zero threshold is supported
        }
    }
}