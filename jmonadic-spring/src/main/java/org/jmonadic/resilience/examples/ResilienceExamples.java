package org.jmonadic.resilience.examples;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

import org.jmonadic.patterns.Result;
import org.jmonadic.resilience.Resilience4jCircuitBreaker;
import org.jmonadic.resilience.Resilience4jRetryPolicy;
import org.springframework.stereotype.Component;

/**
 * Examples demonstrating JMonadic integration with Resilience4j patterns.
 * 
 * This component showcases how to use circuit breakers and retry policies
 * with JMonadic Result types for building resilient Spring applications.
 */
@Component
public class ResilienceExamples {
    
    private final Resilience4jCircuitBreaker defaultCircuitBreaker;
    private final Resilience4jRetryPolicy defaultRetryPolicy;
    
    public ResilienceExamples() {
        // Initialize with production-ready configurations
        this.defaultCircuitBreaker = Resilience4jCircuitBreaker.Presets.resilientCircuitBreaker();
        this.defaultRetryPolicy = Resilience4jRetryPolicy.Presets.resilientRetry();
    }
    
    /**
     * Example 1: Basic circuit breaker usage with Result types
     */
    public Result<String, Exception> callExternalService() {
        return defaultCircuitBreaker.execute(() -> {
            // Simulate external service call
            if (Math.random() < 0.3) {
                throw new RuntimeException("Service temporarily unavailable");
            }
            return "Service response: " + System.currentTimeMillis();
        });
    }
    
    /**
     * Example 2: Retry policy with exponential backoff
     */
    public Result<Integer, Exception> processWithRetry(int value) {
        return defaultRetryPolicy.execute(() -> {
            // Simulate processing that might fail
            if (value < 0) {
                throw new IllegalArgumentException("Value must be positive");
            }
            if (Math.random() < 0.4) {
                throw new RuntimeException("Temporary processing error");
            }
            return value * 2;
        });
    }
    
    /**
     * Example 3: Combining circuit breaker and retry for maximum resilience
     */
    public Result<String, Exception> resilientDatabaseCall(String query) {
        Resilience4jCircuitBreaker dbCircuitBreaker = Resilience4jCircuitBreaker.builder()
            .name("DatabaseCircuitBreaker")
            .failureRateThreshold(60.0f)
            .minimumNumberOfCalls(5)
            .waitDurationInOpenState(Duration.ofSeconds(30))
            .build();
        
        Resilience4jRetryPolicy dbRetryPolicy = Resilience4jRetryPolicy.builder()
            .name("DatabaseRetry")
            .maxAttempts(3)
            .waitDuration(Duration.ofMillis(500))
            .exponentialBackoff(2.0)
            .retryOnException(ex -> 
                ex instanceof java.sql.SQLException ||
                ex.getMessage().contains("connection")
            )
            .build();
        
        return dbRetryPolicy.executeWithCircuitBreaker(() -> {
            // Simulate database call
            if (Math.random() < 0.25) {
                throw new RuntimeException("Database connection timeout");
            }
            return "Query result for: " + query;
        }, dbCircuitBreaker);
    }
    
    /**
     * Example 4: Custom error mapping with typed errors
     */
    public Result<String, ServiceError> callServiceWithTypedErrors() {
        return defaultCircuitBreaker.execute(
            () -> {
                double random = Math.random();
                if (random < 0.2) {
                    throw new RuntimeException("Network error");
                } else if (random < 0.4) {
                    throw new IllegalArgumentException("Invalid request");
                }
                return "Success response";
            },
            exception -> {
                if (exception.getMessage().contains("Network")) {
                    return ServiceError.NETWORK_ERROR;
                } else if (exception instanceof IllegalArgumentException) {
                    return ServiceError.INVALID_REQUEST;
                } else {
                    return ServiceError.UNKNOWN_ERROR;
                }
            }
        );
    }
    
    /**
     * Example 5: Async processing with CompletableFuture
     */
    public CompletableFuture<Result<String, Exception>> asyncProcessing() {
        return CompletableFuture.supplyAsync(() -> 
            defaultRetryPolicy.execute(() -> {
                // Simulate async work
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted", e);
                }
                
                if (Math.random() < 0.3) {
                    throw new RuntimeException("Async processing failed");
                }
                
                return "Async result: " + Thread.currentThread().getName();
            })
        );
    }
    
    /**
     * Example 6: Network service with specific retry configuration
     */
    public Result<String, Exception> callNetworkService(String endpoint) {
        Resilience4jRetryPolicy networkRetry = Resilience4jRetryPolicy.Presets.networkRetry();
        Resilience4jCircuitBreaker networkCircuitBreaker = Resilience4jCircuitBreaker.Presets.networkCircuitBreaker();
        
        return networkRetry.executeWithCircuitBreaker(() -> {
            // Simulate network call
            if (Math.random() < 0.35) {
                throw new RuntimeException("Request timeout");
            }
            return "Response from " + endpoint + ": OK";
        }, networkCircuitBreaker);
    }
    
    /**
     * Example 7: Void operations with error handling
     */
    public Result<Void, Exception> performMaintenanceTask() {
        return defaultCircuitBreaker.executeVoid(() -> {
            // Simulate maintenance operation
            if (Math.random() < 0.2) {
                throw new RuntimeException("Maintenance failed");
            }
            
            // Perform maintenance work
            System.out.println("Maintenance task completed at " + System.currentTimeMillis());
        });
    }
    
    /**
     * Example 8: Functional composition with Result chaining
     */
    public Result<String, Exception> complexWorkflow(String input) {
        return defaultRetryPolicy.execute(() -> validateInput(input))
            .flatMap(validInput -> 
                defaultCircuitBreaker.execute(() -> processInput(validInput)))
            .flatMap(processedData -> 
                defaultRetryPolicy.execute(() -> saveResult(processedData)));
    }
    
    private String validateInput(String input) {
        if (input == null || input.trim().isEmpty()) {
            throw new IllegalArgumentException("Input cannot be empty");
        }
        return input.trim().toUpperCase();
    }
    
    private String processInput(String input) {
        if (Math.random() < 0.3) {
            throw new RuntimeException("Processing service unavailable");
        }
        return "Processed: " + input;
    }
    
    private String saveResult(String data) {
        if (Math.random() < 0.2) {
            throw new RuntimeException("Storage service unavailable");
        }
        return "Saved: " + data;
    }
    
    /**
     * Custom error enumeration for typed error handling
     */
    public enum ServiceError {
        NETWORK_ERROR("Network connectivity issue"),
        INVALID_REQUEST("Request validation failed"),
        SERVICE_UNAVAILABLE("Service temporarily unavailable"),
        TIMEOUT("Operation timed out"),
        UNKNOWN_ERROR("Unknown error occurred");
        
        private final String message;
        
        ServiceError(String message) {
            this.message = message;
        }
        
        public String getMessage() {
            return message;
        }
        
        @Override
        public String toString() {
            return name() + ": " + message;
        }
    }
}