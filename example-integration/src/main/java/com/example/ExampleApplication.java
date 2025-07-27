package com.example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.*;

import org.jmonadic.patterns.Result;
import org.jmonadic.resilience.CircuitBreaker;
import org.jmonadic.observability.StructuredLogger;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Example application demonstrating how to use the Exception Handling Toolkit
 * as a library in your own projects.
 */
@SpringBootApplication
public class ExampleApplication {

    public static void main(String[] args) {
        SpringApplication.run(ExampleApplication.class, args);
    }

    @RestController
    @RequestMapping("/api")
    public static class ExampleController {
        
        private final StructuredLogger logger = StructuredLogger.builder()
            .component("ExampleController")
            .withApplication("example-app")
            .build();
        
        private final CircuitBreaker circuitBreaker = CircuitBreaker.builder()
            .name("ExampleService")
            .failureThreshold(3)
            .waitDurationInOpenState(Duration.ofSeconds(10))
            .build();

        /**
         * Example endpoint using Result pattern for clean error handling.
         */
        @GetMapping("/users/{id}")
        public Result<UserDto, String> getUser(@PathVariable Long id) {
            StructuredLogger.LogContext context = StructuredLogger.LogContext.create()
                .withRequestId(java.util.UUID.randomUUID().toString())
                .withOperation("get_user")
                .with("user_id", id);

            if (id <= 0) {
                return Result.<UserDto, String>failure("Invalid user ID");
            }
            
            return circuitBreaker.execute(() -> {
                // Simulate external service call
                if (ThreadLocalRandom.current().nextDouble() < 0.3) {
                    throw new RuntimeException("Service temporarily unavailable");
                }
                
                return new UserDto(id, "User" + id, "user" + id + "@example.com");
            }).mapError(error -> "Circuit breaker: " + error.getMessage());
        }

        /**
         * Example endpoint demonstrating validation with Either pattern.
         */
        @PostMapping("/users")
        public Result<UserDto, String> createUser(@RequestBody CreateUserRequest request) {
            return validateUser(request)
                .toResult()
                .flatMap(this::saveUser)
                .peekSuccess(user -> logger.logSuccess("createUser", user, 
                    StructuredLogger.LogContext.create().with("user_id", user.id())))
                .peekError(error -> logger.logFailure("createUser", new RuntimeException(error), 
                    StructuredLogger.LogContext.create()));
        }

        private org.jmonadic.patterns.Either<String, CreateUserRequest> validateUser(CreateUserRequest request) {
            if (request.name() == null || request.name().trim().isEmpty()) {
                return org.jmonadic.patterns.Either.left("Name is required");
            }
            if (request.email() == null || !request.email().contains("@")) {
                return org.jmonadic.patterns.Either.left("Valid email is required");
            }
            return org.jmonadic.patterns.Either.right(request);
        }

        private Result<UserDto, String> saveUser(CreateUserRequest request) {
            // Simulate save operation
            Long id = ThreadLocalRandom.current().nextLong(1, 1000);
            return Result.success(new UserDto(id, request.name(), request.email()));
        }

        /**
         * Health check endpoint showing circuit breaker status.
         */
        @GetMapping("/health")
        public java.util.Map<String, Object> health() {
            var metrics = circuitBreaker.getMetrics();
            return java.util.Map.of(
                "status", "UP",
                "circuitBreaker", java.util.Map.of(
                    "name", metrics.name(),
                    "state", metrics.state().toString(),
                    "failureCount", metrics.failureCount()
                )
            );
        }
    }

    // DTOs
    public record UserDto(Long id, String name, String email) {}
    
    public record CreateUserRequest(String name, String email) {}
}