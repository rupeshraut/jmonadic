package org.jmonadic.external;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.RestClientException;

import org.jmonadic.patterns.Result;
import org.jmonadic.resilience.CircuitBreaker;
import org.jmonadic.resilience.RetryPolicy;
import org.jmonadic.observability.StructuredLogger;
import org.jmonadic.observability.TraceableResult;
import org.jmonadic.testing.ChaosEngineering;

import io.opentelemetry.api.trace.SpanKind;

/**
 * Example external service client demonstrating production-ready
 * exception handling for service-to-service communication.
 * 
 * Features:
 * - Circuit breaker protection
 * - Retry policies with exponential backoff
 * - Distributed tracing
 * - Structured logging
 * - Chaos engineering for testing
 */
@Component
public class ExternalServiceClient {
    
    private static final StructuredLogger logger = StructuredLogger.builder()
        .component("ExternalServiceClient")
        .withApplication("exception-showcase")
        .build();
    
    private final RestTemplate restTemplate;
    private final CircuitBreaker circuitBreaker;
    private final RetryPolicy retryPolicy;
    private final TraceableResult tracer;
    private final ChaosEngineering chaosEngineering;
    
    public ExternalServiceClient() {
        this.restTemplate = new RestTemplate();
        
        this.circuitBreaker = CircuitBreaker.builder()
            .name("ExternalServiceAPI")
            .failureThreshold(5)
            .successThreshold(3)
            .waitDurationInOpenState(Duration.ofSeconds(30))
            .timeout(Duration.ofSeconds(5))
            .build();
        
        this.retryPolicy = RetryPolicy.builder()
            .name("ExternalServiceRetry")
            .maxAttempts(3)
            .initialDelay(Duration.ofMillis(500))
            .backoffMultiplier(2.0)
            .jitterFactor(0.1)
            .build();
        
        // For demo purposes, create mock tracer - in production you'd inject OpenTelemetry
        this.tracer = null; // Would be injected in real application
        
        this.chaosEngineering = ChaosEngineering.builder()
            .enabled(false) // Enable for testing
            .failureProbability(0.1)
            .latencyRange(100, 2000)
            .build();
    }
    
    /**
     * Calls an external user service to retrieve user data.
     */
    public Result<ExternalUser, Exception> getUser(Long userId) {
        StructuredLogger.LogContext context = StructuredLogger.LogContext.create()
            .withOperation("get_external_user")
            .with("user_id", userId);
        
        return retryPolicy.executeWithCircuitBreaker(() -> {
            return chaosEngineering.chaosWrap("external_user_api", () -> {
                        // Simulate external API call
                        String url = "https://api.example.com/users/" + userId;
                        
                        try {
                            // Simulate network call
                            Thread.sleep(ThreadLocalRandom.current().nextInt(100, 500));
                            
                            // Simulate occasional failures
                            if (ThreadLocalRandom.current().nextDouble() < 0.1) {
                                throw new RestClientException("External service temporarily unavailable");
                            }
                            
                            // Mock successful response
                            return new ExternalUser(userId, "User" + userId, "user" + userId + "@example.com");
                            
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new RuntimeException("Request interrupted", e);
                        }
                    }).fold(
                        user -> user,
                        error -> { throw new RuntimeException(error); }
                    );
        }, circuitBreaker);
    }
    
    /**
     * Calls multiple external services in parallel with proper error handling.
     */
    public CompletableFuture<Result<UserProfile, Exception>> getUserProfile(Long userId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                ExternalUser user = getUser(userId).getValue();
                UserPreferences prefs = getUserPreferences(userId).getOrElse(UserPreferences.defaultPreferences());
                UserStatistics stats = getUserStatistics(userId).getOrElse(UserStatistics.empty());
                
                UserProfile profile = new UserProfile(user, prefs, stats);
                return Result.<UserProfile, Exception>success(profile);
            } catch (Exception e) {
                return Result.<UserProfile, Exception>failure(e);
            }
        });
    }
    
    /**
     * Retrieves user preferences with fallback to defaults.
     */
    public Result<UserPreferences, Exception> getUserPreferences(Long userId) {
        return retryPolicy.executeWithCircuitBreaker(() -> {
            try {
                Thread.sleep(ThreadLocalRandom.current().nextInt(50, 200));
                
                if (ThreadLocalRandom.current().nextDouble() < 0.15) {
                    throw new RestClientException("Preferences service unavailable");
                }
                
                return new UserPreferences(userId, "en-US", "dark", true);
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Request interrupted", e);
            }
        }, circuitBreaker);
    }
    
    /**
     * Retrieves user statistics with graceful degradation.
     */
    public Result<UserStatistics, Exception> getUserStatistics(Long userId) {
        return retryPolicy.executeWithCircuitBreaker(() -> {
            try {
                Thread.sleep(ThreadLocalRandom.current().nextInt(100, 300));
                
                if (ThreadLocalRandom.current().nextDouble() < 0.2) {
                    throw new RestClientException("Statistics service overloaded");
                }
                
                return new UserStatistics(userId, 
                    ThreadLocalRandom.current().nextInt(0, 1000),
                    ThreadLocalRandom.current().nextInt(0, 100));
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Request interrupted", e);
            }
        }, circuitBreaker);
    }
    
    /**
     * Demonstrates webhook-style external communication with error handling.
     */
    public Result<Void, Exception> sendNotification(Long userId, String message) {
        StructuredLogger.LogContext context = StructuredLogger.LogContext.create()
            .withOperation("send_notification")
            .with("user_id", userId)
            .with("message_length", message.length());
        
        return retryPolicy.execute(() -> {
            try {
                Thread.sleep(ThreadLocalRandom.current().nextInt(100, 500));
                
                if (ThreadLocalRandom.current().nextDouble() < 0.05) {
                    throw new RestClientException("Notification service unavailable");
                }
                
                // Simulate successful notification
                return null;
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Request interrupted", e);
            }
        });
    }
    
    /**
     * Gets circuit breaker metrics for monitoring.
     */
    public CircuitBreaker.Metrics getCircuitBreakerMetrics() {
        return circuitBreaker.getMetrics();
    }
    
    /**
     * Enables or disables chaos engineering for testing.
     */
    public void setChaosEnabled(boolean enabled) {
        chaosEngineering.setEnabled(enabled);
    }
    
    // DTOs for external service responses
    public record ExternalUser(Long id, String name, String email) {}
    
    public record UserPreferences(Long userId, String language, String theme, boolean notifications) {
        public static UserPreferences defaultPreferences() {
            return new UserPreferences(null, "en-US", "light", true);
        }
    }
    
    public record UserStatistics(Long userId, int loginCount, int actionsCount) {
        public static UserStatistics empty() {
            return new UserStatistics(null, 0, 0);
        }
    }
    
    public record UserProfile(ExternalUser user, UserPreferences preferences, UserStatistics statistics) {}
}