package org.jmonadic.examples;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.jmonadic.patterns.Result;
import org.jmonadic.patterns.Either;
import org.jmonadic.patterns.Try;
import org.jmonadic.performance.ZeroAllocationException;
import org.jmonadic.performance.FastFailResult;
import org.jmonadic.performance.BenchmarkRunner;
import org.jmonadic.observability.StructuredLogger;
import org.jmonadic.resilience.CircuitBreaker;
import org.jmonadic.resilience.RetryPolicy;
import org.jmonadic.testing.ChaosEngineering;
import org.jmonadic.utils.ExceptionUtils;
import org.jmonadic.external.ExternalServiceClient;

/**
 * Comprehensive example runner demonstrating all production-ready
 * exception handling patterns and features.
 */
@Component
public class ProductionExampleRunner {
    
    private static final Logger logger = LoggerFactory.getLogger(ProductionExampleRunner.class);
    
    private static final StructuredLogger structuredLogger = StructuredLogger.builder()
        .component("ProductionExampleRunner")
        .withApplication("exception-showcase")
        .withEnvironment("demo")
        .build();
    
    @Autowired(required = false)
    private ExternalServiceClient externalServiceClient;
    
    public void runAllProductionExamples() {
        logger.info("🚀 === PRODUCTION-READY EXCEPTION HANDLING SHOWCASE ===");
        
        runPerformanceOptimizationExamples();
        runObservabilityExamples();
        runResilienceExamples();
        runChaosEngineeringExamples();
        runExternalServiceExamples();
        runUtilityExamples();
        runBenchmarkingExamples();
        
        logger.info("✅ === ALL PRODUCTION EXAMPLES COMPLETED ===");
    }
    
    private void runPerformanceOptimizationExamples() {
        logger.info("\n⚡ === PERFORMANCE OPTIMIZATION EXAMPLES ===");
        
        // Zero-allocation exception handling
        try {
            ZeroAllocationException exception = ZeroAllocationException.of(
                "PERF001", 
                "High-performance error without stack trace",
                ZeroAllocationException.context()
                    .withUserId("user123")
                    .withOperation("critical_path")
                    .build(),
                false // No stack trace for performance
            );
            
            logger.info("🔥 Zero-allocation exception created: {}", exception.getErrorCode());
            exception.release(); // Return to pool
            
        } catch (Exception e) {
            logger.error("Performance example failed", e);
        }
        
        // Fast-fail result patterns
        Result<String, String> fastResult = FastFailResult.<String>emptySuccess()
            .map(v -> "Fast operation completed")
            .map(String::toUpperCase);
        
        logger.info("⚡ Fast-fail result: {}", fastResult.getValue());
        
        // Pre-allocated common results
        Result<Boolean, String> authResult = FastFailResult.booleanSuccess(true);
        Result<String, String> notFoundResult = FastFailResult.notFoundError();
        
        logger.info("✅ Pre-allocated results - Auth: {}, NotFound: {}", 
                   authResult.isSuccess(), notFoundResult.isFailure());
    }
    
    private void runObservabilityExamples() {
        logger.info("\n📊 === OBSERVABILITY EXAMPLES ===");
        
        // Structured logging with context
        StructuredLogger.LogContext context = StructuredLogger.LogContext.create()
            .withUserId("user456")
            .withRequestId("req-789")
            .withCorrelationId("corr-abc")
            .withOperation("business_operation");
        
        // Successful operation
        structuredLogger.logSuccess("businessOperation", "Operation completed successfully", context);
        
        // Failed operation with rich context
        Exception businessError = new RuntimeException("Business logic validation failed");
        structuredLogger.logFailure("businessValidation", businessError, 
            context.withMetric("validation_time_ms", 45));
        
        // Performance logging
        structuredLogger.logPerformance("databaseQuery", 150, 
            context.withMetric("query_complexity", "high"));
        
        // Security event logging
        structuredLogger.logSecurityEvent("login_attempt", "user456", "/api/login", true, context);
        
        logger.info("📋 Structured logs generated with JSON format");
    }
    
    private void runResilienceExamples() {
        logger.info("\n🛡️ === RESILIENCE EXAMPLES ===");
        
        // Circuit breaker with comprehensive configuration
        CircuitBreaker criticalServiceBreaker = CircuitBreaker.builder()
            .name("CriticalService")
            .failureThreshold(3)
            .successThreshold(2)
            .waitDurationInOpenState(Duration.ofSeconds(10))
            .timeout(Duration.ofSeconds(2))
            .build();
        
        // Simulate service calls with circuit breaker
        for (int i = 1; i <= 8; i++) {
            final int callId = i;
            Result<String, CircuitBreaker.CircuitBreakerException> result = 
                criticalServiceBreaker.execute(() -> {
                    // Simulate failures in first 4 calls
                    if (callId <= 4) {
                        throw new RuntimeException("Service temporarily down");
                    }
                    return "Service call " + callId + " succeeded";
                });
            
            String status = result.isSuccess() ? "✅ SUCCESS" : "❌ FAILED";
            logger.info("{} - Call {}: {} (Circuit: {})", 
                       status, callId, 
                       result.fold(r -> r, e -> e.getMessage()),
                       criticalServiceBreaker.getState());
        }
        
        // Retry policy with advanced configuration
        RetryPolicy smartRetry = RetryPolicy.builder()
            .name("SmartRetry")
            .maxAttempts(4)
            .initialDelay(Duration.ofMillis(200))
            .backoffMultiplier(1.5)
            .jitterFactor(0.2)
            .build();
        
        Result<String, Exception> retryResult = smartRetry.execute(() -> {
            if (Math.random() < 0.7) { // 70% failure rate
                throw new RuntimeException("Transient failure");
            }
            return "Retry operation succeeded!";
        });
        
        String retryMessage = retryResult.fold(r -> r, e -> "All retries failed: " + e.getMessage());
        logger.info("🔄 Retry result: {}", retryMessage);
    }
    
    private void runChaosEngineeringExamples() {
        logger.info("\n🔥 === CHAOS ENGINEERING EXAMPLES ===");
        
        ChaosEngineering chaosTest = ChaosEngineering.builder()
            .enabled(true)
            .failureProbability(0.3) // 30% chaos injection
            .latencyRange(100, 1000)
            .build();
        
        // Test system resilience with chaos
        for (int i = 1; i <= 5; i++) {
            final int operationId = i;
            
            Result<String, Exception> chaosResult = chaosTest.chaosWrap(
                "criticalOperation" + operationId,
                () -> "Critical operation " + operationId + " completed"
            );
            
            String outcome = chaosResult.isSuccess() ? "✅ SURVIVED" : "💥 CHAOS INJECTED";
            logger.info("{} - Operation {}: {}", 
                       outcome, operationId,
                       chaosResult.fold(r -> r, e -> e.getMessage()));
        }
        
        chaosTest.setEnabled(false);
        logger.info("🧪 Chaos engineering tests completed");
    }
    
    private void runExternalServiceExamples() {
        logger.info("\n🌐 === EXTERNAL SERVICE INTEGRATION EXAMPLES ===");
        
        if (externalServiceClient != null) {
            // Single service call with full error handling
            Result<ExternalServiceClient.ExternalUser, Exception> userResult = 
                externalServiceClient.getUser(123L);
            
            userResult.fold(
                user -> {
                    logger.info("✅ External user retrieved: {}", user.name());
                    return null;
                },
                error -> {
                    logger.warn("❌ External user fetch failed: {}", error.getMessage());
                    return null;
                }
            );
            
            // Parallel service composition
            CompletableFuture<Result<ExternalServiceClient.UserProfile, Exception>> profileFuture = 
                externalServiceClient.getUserProfile(456L);
            
            profileFuture.thenAccept(profileResult -> {
                profileResult.fold(
                    profile -> {
                        logger.info("✅ User profile assembled: {} ({})", 
                                   profile.user().name(), profile.preferences().theme());
                        return null;
                    },
                    error -> {
                        logger.warn("❌ Profile assembly failed: {}", error.getMessage());
                        return null;
                    }
                );
            });
            
            // Circuit breaker metrics
            CircuitBreaker.Metrics metrics = externalServiceClient.getCircuitBreakerMetrics();
            logger.info("📊 External service circuit breaker - State: {}, Failures: {}", 
                       metrics.state(), metrics.failureCount());
        } else {
            logger.info("📝 External service client not available (demo mode)");
        }
    }
    
    private void runUtilityExamples() {
        logger.info("\n🔧 === UTILITY EXAMPLES ===");
        
        // Validation utilities
        Result<String, String> emailValidation = ExceptionUtils.Validation
            .requireEmail("user@example.com");
        
        Result<Integer, String> ageValidation = ExceptionUtils.Validation
            .requirePositive(25, "Age");
        
        logger.info("✅ Email validation: {}", emailValidation.isSuccess());
        logger.info("✅ Age validation: {}", ageValidation.getValue());
        
        // Safe parsing
        Result<Integer, NumberFormatException> parseResult = ExceptionUtils.parseInt("42");
        Result<Integer, NumberFormatException> parseFailure = ExceptionUtils.parseInt("not-a-number");
        
        logger.info("🔢 Parse success: {}, Parse failure: {}", 
                   parseResult.getValue(), parseFailure.isFailure());
        
        // Collection operations
        List<Result<String, String>> results = List.of(
            Result.success("A"),
            Result.success("B"),
            Result.failure("Error C"),
            Result.success("D")
        );
        
        Result<List<String>, String> sequenceResult = ExceptionUtils.sequence(results);
        List<String> successfulResults = ExceptionUtils.collectSuccessful(results);
        
        logger.info("📋 Sequence result: {}, Successful count: {}", 
                   sequenceResult.isFailure(), successfulResults.size());
        
        // Timed operations
        ExceptionUtils.TimedResult<String> timedResult = ExceptionUtils.time(() -> {
            try {
                Thread.sleep(100); // Simulate work
                return Result.success("Timed operation completed");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return Result.failure(e);
            }
        });
        
        logger.info("⏱️ Operation took {:.2f}ms: {}", 
                   timedResult.getDurationMillis(),
                   timedResult.isSuccess() ? "SUCCESS" : "FAILED");
    }
    
    private void runBenchmarkingExamples() {
        logger.info("\n🏁 === BENCHMARKING EXAMPLES ===");
        
        logger.info("🔥 Running performance benchmarks...");
        logger.info("📊 (Check logs above for detailed benchmark results)");
        
        // Note: BenchmarkRunner.main() contains comprehensive benchmarks
        // This is a lighter version for integration
        
        // Simple performance comparison
        long iterations = 10_000;
        
        // Traditional approach
        long start = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            try {
                @SuppressWarnings("unused")
                String result = "Traditional " + i;
            } catch (Exception e) {
                // Handle
            }
        }
        long traditionalTime = System.nanoTime() - start;
        
        // Result pattern approach
        start = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            final int value = i;
            @SuppressWarnings("unused")
            Result<String, Exception> result = Result.of(() -> "Result " + value);
        }
        long resultTime = System.nanoTime() - start;
        
        double ratio = (double) resultTime / traditionalTime;
        logger.info("📈 Performance ratio (Result/Traditional): {:.2f}x", ratio);
        logger.info("⚡ Traditional: {:.2f}ms, Result Pattern: {:.2f}ms", 
                   traditionalTime / 1_000_000.0, resultTime / 1_000_000.0);
        
        if (ratio < 1.5) {
            logger.info("✅ Result pattern overhead is acceptable (< 1.5x)");
        } else {
            logger.info("⚠️ Result pattern has significant overhead ({}x)", ratio);
        }
    }
}