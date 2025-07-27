package org.jmonadic.examples;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import org.jmonadic.patterns.Either;
import org.jmonadic.patterns.Result;
import org.jmonadic.patterns.Try;
import org.jmonadic.resilience.CircuitBreaker;
import org.jmonadic.resilience.RetryPolicy;

import org.springframework.beans.factory.annotation.Autowired;

/**
 * Comprehensive examples demonstrating modern Java exception handling patterns.
 */
@Component
public class ExampleRunner {
    
    private static final Logger logger = LoggerFactory.getLogger(ExampleRunner.class);
    
    @Autowired(required = false)
    private ProductionExampleRunner productionRunner;
    
    public void runAllExamples() {
        logger.info("🎯 === MODERN JAVA EXCEPTION HANDLING SHOWCASE ===");
        
        runResultPatternExamples();
        runEitherPatternExamples(); 
        runTryPatternExamples();
        runCircuitBreakerExamples();
        runRetryPolicyExamples();
        runCompositionExamples();
        runPerformanceExamples();
        
        // Run production-ready examples
        if (productionRunner != null) {
            logger.info("\n🚀 === RUNNING PRODUCTION-READY EXAMPLES ===");
            productionRunner.runAllProductionExamples();
        }
    }
    
    private void runResultPatternExamples() {
        logger.info("\n📊 === RESULT PATTERN EXAMPLES ===");
        
        // Basic Result usage
        Result<String, String> success = Result.success("Hello World");
        Result<String, String> failure = Result.failure("Something went wrong");
        
        logger.info("✅ Success result: {}", success.getValue());
        logger.info("❌ Failure result: {}", failure.getError());
        
        // Chaining operations
        Result<Integer, Exception> result = Result.of(() -> "42")
            .map(Integer::parseInt)
            .map(i -> i * 2)
            .peekSuccess(value -> logger.info("🔗 Computed value: {}", value))
            .peekError(error -> logger.error("💥 Computation failed", error));
        
        if (result.isSuccess()) {
            logger.info("🎉 Final result: {}", result.getValue());
        }
        
        // Error recovery - demonstrating error recovery patterns
        Result<String, Exception> recovered = Result.<String, Exception>failure(
            new RuntimeException("Simulated failure")
        ).recoverWith(error -> "Recovered: " + error.getMessage());
        
        logger.info("🔄 Recovered result: {}", recovered.getValue());
    }
    
    private void runEitherPatternExamples() {
        logger.info("\n⚖️ === EITHER PATTERN EXAMPLES ===");
        
        // Either for validation
        Either<String, Integer> validAge = validateAge(25);
        Either<String, Integer> invalidAge = validateAge(-5);
        
        validAge.<Void>fold(
            error -> { logger.error("❌ Validation failed: {}", error); return null; },
            age -> { logger.info("✅ Valid age: {}", age); return null; }
        );
        
        invalidAge.<Void>fold(
            error -> { logger.error("❌ Validation failed: {}", error); return null; },
            age -> { logger.info("✅ Valid age: {}", age); return null; }
        );
        
        // Chaining Either operations
        @SuppressWarnings("unused")
        Either<String, String> processedUser = validateAge(30)
            .map(age -> "User with age: " + age)
            .map(String::toUpperCase)
            .peekRight(user -> logger.info("👤 Processed user: {}", user));
    }
    
    private Either<String, Integer> validateAge(int age) {
        if (age < 0) {
            return Either.left("Age cannot be negative");
        }
        if (age > 150) {
            return Either.left("Age seems too high");
        }
        return Either.right(age);
    }
    
    private void runTryPatternExamples() {
        logger.info("\n🎲 === TRY PATTERN EXAMPLES ===");
        
        // Safe parsing with Try
        Try<Integer> parsed = Try.of(() -> Integer.parseInt("123"));
        Try<Integer> failed = Try.of(() -> Integer.parseInt("abc"));
        
        parsed.map(i -> i * 2)
               .recover(ex -> -1)
               .toOptional()
               .ifPresent(value -> logger.info("✅ Parsed and doubled: {}", value));
        
        failed.recover(ex -> {
            logger.warn("🔧 Parsing failed, using default: {}", ex.getMessage());
            return 0;
        }).toOptional()
          .ifPresent(value -> logger.info("🔄 Recovered value: {}", value));
        
        // Converting between types
        Result<Integer, Exception> tryToResult = failed.toResult();
        logger.info("🔄 Try converted to Result: {}", tryToResult.isFailure() ? "Failed" : "Success");
    }
    
    private void runCircuitBreakerExamples() {
        logger.info("\n🔌 === CIRCUIT BREAKER EXAMPLES ===");
        
        CircuitBreaker circuitBreaker = CircuitBreaker.builder()
            .name("ExampleService")
            .failureThreshold(3)
            .waitDurationInOpenState(Duration.ofSeconds(5))
            .timeout(Duration.ofSeconds(1))
            .build();
        
        // Simulate service calls
        for (int i = 1; i <= 10; i++) {
            final int callNumber = i;
            Result<String, CircuitBreaker.CircuitBreakerException> result = 
                circuitBreaker.execute(() -> simulateServiceCall(callNumber));
            
            if (result.isSuccess()) {
                logger.info("🟢 Call {}: {}", callNumber, result.getValue());
            } else {
                logger.warn("🔴 Call {}: {}", callNumber, result.getError().getMessage());
            }
            
            // Log circuit breaker state
            CircuitBreaker.Metrics metrics = circuitBreaker.getMetrics();
            logger.info("📊 Circuit breaker state: {} (failures: {})", 
                       metrics.state(), metrics.failureCount());
            
            // Brief pause between calls
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
    
    private String simulateServiceCall(int callNumber) {
        // Simulate failures for calls 2-5 to trigger circuit breaker
        if (callNumber >= 2 && callNumber <= 5) {
            throw new RuntimeException("Service temporarily unavailable");
        }
        return "Service response for call " + callNumber;
    }
    
    private void runRetryPolicyExamples() {
        logger.info("\n🔄 === RETRY POLICY EXAMPLES ===");
        
        RetryPolicy retryPolicy = RetryPolicy.builder()
            .name("ExampleRetry")
            .maxAttempts(3)
            .initialDelay(Duration.ofMillis(100))
            .backoffMultiplier(2.0)
            .jitterFactor(0.1)
            .build();
        
        // Simulate a service that fails twice then succeeds
        Result<String, Exception> result = retryPolicy.execute(() -> {
            if (ThreadLocalRandom.current().nextBoolean()) {
                throw new RuntimeException("Random failure");
            }
            return "Operation succeeded!";
        });
        
        result.<Void>fold(
            value -> { logger.info("✅ Retry succeeded: {}", value); return null; },
            error -> { logger.error("❌ Retry failed: {}", error.getMessage()); return null; }
        );
        
        // Async retry example
        CompletableFuture<Result<String, Exception>> asyncResult = 
            retryPolicy.executeAsync(() -> "Async operation completed!");
        
        asyncResult.thenAccept(res -> {
            if (res.isSuccess()) {
                logger.info("🚀 Async retry succeeded: {}", res.getValue());
            } else {
                logger.error("💥 Async retry failed: {}", res.getError().getMessage());
            }
        });
    }
    
    private void runCompositionExamples() {
        logger.info("\n🧩 === COMPOSITION EXAMPLES ===");
        
        // Combining Circuit Breaker with Retry Policy
        CircuitBreaker cb = CircuitBreaker.builder()
            .name("ComposedService")
            .failureThreshold(2)
            .build();
        
        RetryPolicy retry = RetryPolicy.Presets.quickRetry();
        
        Result<String, Exception> composed = retry.executeWithCircuitBreaker(
            () -> "Composed operation successful!",
            cb
        );
        
        composed.<Void>fold(
            value -> { logger.info("🎯 Composed operation: {}", value); return null; },
            error -> { logger.error("💥 Composed operation failed: {}", error.getMessage()); return null; }
        );
        
        // Pipeline of transformations
        Result<Integer, Exception> pipeline = Result.of(() -> "  42  ")
            .map(String::trim)
            .map(Integer::parseInt)
            .map(i -> i * 2)
            .filter(i -> i > 0, () -> new IllegalArgumentException("Value must be positive"));
            
        pipeline.peekSuccess(value -> logger.info("🔄 Pipeline result: {}", value));
    }
    
    private void runPerformanceExamples() {
        logger.info("\n⚡ === PERFORMANCE EXAMPLES ===");
        
        // Measure overhead of different approaches
        int iterations = 100_000;
        
        // Traditional try-catch
        long start = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            try {
                @SuppressWarnings("unused")
                String result = String.valueOf(i);
            } catch (Exception e) {
                // Handle error
            }
        }
        long traditionalTime = System.nanoTime() - start;
        
        // Result pattern
        start = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            final int value = i;
            @SuppressWarnings("unused")
            Result<String, Exception> result = Result.of(() -> String.valueOf(value));
        }
        long resultTime = System.nanoTime() - start;
        
        logger.info("⏱️ Traditional try-catch: {} ns", traditionalTime);
        logger.info("⏱️ Result pattern: {} ns", resultTime);
        logger.info("📈 Overhead ratio: {:.2f}x", (double) resultTime / traditionalTime);
    }
}
