# Pro Tips & Recipes 💡

This guide contains advanced techniques, proven recipes, and expert-level tips for mastering modern Java exception handling.

## 🎯 Expert Patterns

### 1. Exception Translation Boundaries

Define clear boundaries where exceptions are translated to domain-specific errors.

```java
public class UserServiceBoundary {
    private final UserRepository userRepository;
    
    // Translation boundary: Infrastructure → Domain
    public Result<User, UserServiceError> findUser(String userId) {
        return Result.of(() -> userRepository.findById(userId))
            .mapError(this::translateError)
            .flatMap(optionalUser -> optionalUser
                .map(Result::<User, UserServiceError>success)
                .orElse(Result.failure(UserServiceError.NOT_FOUND)));
    }
    
    private UserServiceError translateError(Exception ex) {
        return switch (ex) {
            case DataAccessException dae -> UserServiceError.DATABASE_UNAVAILABLE;
            case TimeoutException te -> UserServiceError.OPERATION_TIMEOUT;
            case SecurityException se -> UserServiceError.ACCESS_DENIED;
            default -> UserServiceError.UNKNOWN_ERROR;
        };
    }
}
```

### 2. Error Context Enrichment

Preserve and enrich error context as it propagates through layers.

```java
public class ErrorContext {
    private final Map<String, Object> context = new HashMap<>();
    private final String correlationId;
    
    public ErrorContext(String correlationId) {
        this.correlationId = correlationId;
        this.context.put("timestamp", Instant.now());
        this.context.put("thread", Thread.currentThread().getName());
    }
    
    public ErrorContext addContext(String key, Object value) {
        context.put(key, value);
        return this;
    }
    
    public <T, E> Result<T, EnrichedError<E>> enrich(Result<T, E> result) {
        if (result.isFailure()) {
            EnrichedError<E> enriched = new EnrichedError<>(
                result.getError(), correlationId, Map.copyOf(context));
            return Result.failure(enriched);
        }
        return result.map(value -> value).mapError(error -> null); // Won't be called
    }
}

public record EnrichedError<E>(
    E originalError,
    String correlationId,
    Map<String, Object> context
) {
    public String getDetailedMessage() {
        return String.format("[%s] %s - Context: %s", 
                           correlationId, originalError, context);
    }
}
```

### 3. Functional Pipeline Validation

Chain validation operations in a functional style.

```java
public class UserValidator {
    
    public static Result<User, List<String>> validateUser(UserCreateRequest request) {
        return ValidationPipeline.<UserCreateRequest>start()
            .validate(req -> !req.name().isBlank(), "Name cannot be blank")
            .validate(req -> req.age() >= 0, "Age must be non-negative")
            .validate(req -> req.age() <= 150, "Age must be realistic")
            .validate(req -> isValidEmail(req.email()), "Invalid email format")
            .validate(req -> req.password().length() >= 8, "Password too short")
            .execute(request)
            .map(req -> new User(req.name(), req.age(), req.email()));
    }
    
    private static boolean isValidEmail(String email) {
        return email != null && email.contains("@");
    }
}

public class ValidationPipeline<T> {
    private final List<Validator<T>> validators = new ArrayList<>();
    
    public static <T> ValidationPipeline<T> start() {
        return new ValidationPipeline<>();
    }
    
    public ValidationPipeline<T> validate(Predicate<T> condition, String errorMessage) {
        validators.add(new Validator<>(condition, errorMessage));
        return this;
    }
    
    public Result<T, List<String>> execute(T input) {
        List<String> errors = validators.stream()
            .filter(validator -> !validator.condition().test(input))
            .map(Validator::errorMessage)
            .toList();
        
        return errors.isEmpty() 
            ? Result.success(input)
            : Result.failure(errors);
    }
    
    private record Validator<T>(Predicate<T> condition, String errorMessage) {}
}
```

## 🏗️ Architectural Patterns

### 1. Saga Pattern with Error Handling

Implement distributed transaction patterns with comprehensive error handling.

```java
public class OrderSaga {
    
    public Result<OrderResult, SagaError> processOrder(OrderRequest request) {
        return SagaBuilder.<OrderRequest, OrderResult>start()
            .step("reserve-inventory", this::reserveInventory, this::compensateInventory)
            .step("process-payment", this::processPayment, this::refundPayment)
            .step("create-shipment", this::createShipment, this::cancelShipment)
            .step("send-confirmation", this::sendConfirmation, this::sendCancellation)
            .execute(request);
    }
    
    private Result<InventoryReservation, SagaError> reserveInventory(OrderRequest request) {
        return inventoryService.reserve(request.items())
            .mapError(error -> SagaError.INVENTORY_UNAVAILABLE)
            .peekError(error -> logger.warn("Inventory reservation failed: {}", error));
    }
    
    private Result<Void, SagaError> compensateInventory(InventoryReservation reservation) {
        return inventoryService.release(reservation)
            .mapError(error -> SagaError.COMPENSATION_FAILED);
    }
    
    // Other saga steps...
}
```

### 2. CQRS with Error Handling

Separate command and query error handling strategies.

```java
// Command side - focus on business rule violations
public class UserCommandHandler {
    
    public Result<UserId, CommandError> handle(CreateUserCommand command) {
        return validateCommand(command)
            .flatMap(this::checkBusinessRules)
            .flatMap(this::persistUser)
            .map(User::getId);
    }
    
    private Result<CreateUserCommand, CommandError> validateCommand(CreateUserCommand command) {
        return ValidationResult.validate(command)
            .mapError(violations -> CommandError.VALIDATION_FAILED);
    }
    
    private Result<CreateUserCommand, CommandError> checkBusinessRules(CreateUserCommand command) {
        if (userRepository.existsByEmail(command.email())) {
            return Result.failure(CommandError.EMAIL_ALREADY_EXISTS);
        }
        return Result.success(command);
    }
}

// Query side - focus on data availability and performance
public class UserQueryHandler {
    
    public Result<UserView, QueryError> handle(GetUserQuery query) {
        return findUserInCache(query.userId())
            .recover(error -> findUserInDatabase(query.userId()))
            .recover(error -> createFallbackView(query.userId()))
            .mapError(error -> QueryError.USER_NOT_AVAILABLE);
    }
}
```

## 🔥 Advanced Techniques

### 1. Monadic Error Accumulation

Collect multiple errors instead of failing on the first one.

```java
public class ErrorAccumulator<E> {
    private final List<E> errors = new ArrayList<>();
    
    public static <E> ErrorAccumulator<E> empty() {
        return new ErrorAccumulator<>();
    }
    
    public <T> ErrorAccumulator<E> apply(Result<T, E> result, Consumer<T> consumer) {
        if (result.isSuccess()) {
            consumer.accept(result.getValue());
        } else {
            errors.add(result.getError());
        }
        return this;
    }
    
    public <T> Result<T, List<E>> complete(Supplier<T> successValue) {
        return errors.isEmpty() 
            ? Result.success(successValue.get())
            : Result.failure(List.copyOf(errors));
    }
}

// Usage
public Result<ProcessedData, List<ValidationError>> processDataWithAccumulation(RawData data) {
    return ErrorAccumulator.<ValidationError>empty()
        .apply(validateField1(data.field1()), field1 -> data.setProcessedField1(field1))
        .apply(validateField2(data.field2()), field2 -> data.setProcessedField2(field2))
        .apply(validateField3(data.field3()), field3 -> data.setProcessedField3(field3))
        .complete(() -> data.toProcessedData());
}
```

### 2. Aspect-Oriented Error Handling

Use AOP for cross-cutting error handling concerns.

```java
public class ErrorHandlingInterceptor {
    
    @Around("@annotation(handleErrors)")
    public Object handleErrors(ProceedingJoinPoint joinPoint, HandleErrors handleErrors) throws Throwable {
        try {
            Object result = joinPoint.proceed();
            
            // Wrap return value in Result if not already wrapped
            if (!(result instanceof Result)) {
                return Result.success(result);
            }
            
            return result;
        } catch (Exception e) {
            // Transform based on annotation configuration
            Class<? extends Exception>[] retryOn = handleErrors.retryOn();
            Class<? extends Exception>[] failFastOn = handleErrors.failFastOn();
            
            if (Arrays.stream(failFastOn).anyMatch(cls -> cls.isInstance(e))) {
                return Result.failure(e);
            }
            
            if (Arrays.stream(retryOn).anyMatch(cls -> cls.isInstance(e))) {
                return retryWithPolicy(joinPoint, handleErrors.retryPolicy());
            }
            
            return Result.failure(e);
        }
    }
}

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface HandleErrors {
    String retryPolicy() default "default";
    Class<? extends Exception>[] retryOn() default {};
    Class<? extends Exception>[] failFastOn() default {};
}
```

### 3. Event-Driven Error Recovery

Implement error recovery through domain events.

```java
public class OrderEventHandler {
    
    public void handlePaymentFailed(PaymentFailedEvent event) {
        ErrorRecoveryPipeline.builder()
            .step("notify-customer", () -> notificationService.notifyPaymentFailed(event.customerId()))
            .step("release-inventory", () -> inventoryService.release(event.orderId()))
            .step("log-metrics", () -> metricsService.recordPaymentFailure(event))
            .onError(error -> escalateToSupport(event, error))
            .execute();
    }
    
    private void escalateToSupport(PaymentFailedEvent event, Exception error) {
        supportService.createTicket(
            "Payment failure recovery failed",
            String.format("Order: %s, Customer: %s, Error: %s", 
                         event.orderId(), event.customerId(), error.getMessage())
        );
    }
}
```

## 🎮 Testing Strategies

### 1. Property-Based Testing for Error Handling

```java
@ExtendWith(JqwikExtension.class)
class ErrorHandlingProperties {
    
    @Property
    void resultChainingShouldPreserveErrors(
        @ForAll("validStrings") String input,
        @ForAll Random random) {
        
        // Arrange
        Function<String, Result<Integer, String>> parser = s -> 
            random.nextBoolean() 
                ? Result.success(s.length())
                : Result.failure("Parse error");
        
        Function<Integer, Result<String, String>> formatter = i ->
            random.nextBoolean()
                ? Result.success("Value: " + i)
                : Result.failure("Format error");
        
        // Act
        Result<String, String> result = Result.success(input)
            .flatMap(parser)
            .flatMap(formatter);
        
        // Assert - error handling should be consistent
        if (result.isFailure()) {
            assertThat(result.getError()).containsAnyOf("Parse error", "Format error");
        }
    }
    
    @Provide
    Arbitrary<String> validStrings() {
        return Arbitraries.strings().ofMinLength(1).ofMaxLength(100);
    }
}
```

### 2. Chaos Engineering for Resilience

```java
public class ChaosTestConfig {
    
    public ChaosInterceptor createChaosInterceptor() {
        return ChaosInterceptor.builder()
            .failureRate(0.1) // 10% failure rate
            .latencyRate(0.2) // 20% operations get extra latency
            .latencyRange(Duration.ofMillis(100), Duration.ofSeconds(2))
            .build();
    }
}

public class ChaosTestController {
    
    public Result<User, String> getUser(String id) {
        Result<User, String> result = userService.findUser(id)
            .mapError(error -> "User service error: " + error.getMessage());
        
        return result;
    }
}
```

## 🚀 Production Patterns

### 1. Graceful Degradation Framework

```java
public class GracefulDegradationManager {
    private final Map<String, DegradationLevel> serviceLevels = new ConcurrentHashMap<>();
    
    public enum DegradationLevel {
        NORMAL(1.0),
        DEGRADED(0.7),
        MINIMAL(0.3),
        EMERGENCY(0.1);
        
        private final double capacityFactor;
        
        DegradationLevel(double capacityFactor) {
            this.capacityFactor = capacityFactor;
        }
    }
    
    public <T, E> Result<T, E> executeWithDegradation(
            String serviceName,
            Supplier<Result<T, E>> primaryOperation,
            Supplier<Result<T, E>> degradedOperation,
            Supplier<Result<T, E>> minimalOperation) {
        
        DegradationLevel level = serviceLevels.getOrDefault(serviceName, DegradationLevel.NORMAL);
        
        return switch (level) {
            case NORMAL -> primaryOperation.get()
                .recover(error -> {
                    degradeService(serviceName);
                    return degradedOperation.get();
                });
                
            case DEGRADED -> degradedOperation.get()
                .recover(error -> {
                    degradeService(serviceName);
                    return minimalOperation.get();
                });
                
            case MINIMAL, EMERGENCY -> minimalOperation.get();
        };
    }
    
    private void degradeService(String serviceName) {
        serviceLevels.compute(serviceName, (name, currentLevel) -> {
            if (currentLevel == null) return DegradationLevel.DEGRADED;
            
            return switch (currentLevel) {
                case NORMAL -> DegradationLevel.DEGRADED;
                case DEGRADED -> DegradationLevel.MINIMAL;
                default -> currentLevel;
            };
        });
    }
}
```

### 2. Error Budget Management

```java
public class ErrorBudgetManager {
    private static final double SLO_TARGET = 0.999; // 99.9% success rate
    private final SlidingWindowCounter errorCounter;
    private final SlidingWindowCounter totalCounter;
    
    public boolean canAffordError(String operation) {
        double currentErrorRate = calculateErrorRate();
        double errorBudget = 1.0 - SLO_TARGET;
        
        return currentErrorRate < errorBudget * 0.8; // Use 80% of budget
    }
    
    public <T, E> Result<T, E> executeWithBudget(
            String operation,
            Supplier<Result<T, E>> supplier) {
        
        if (!canAffordError(operation)) {
            // Fail fast to preserve error budget
            return Result.failure((E) new ErrorBudgetExceededException(operation));
        }
        
        totalCounter.increment();
        Result<T, E> result = supplier.get();
        
        if (result.isFailure()) {
            errorCounter.increment();
        }
        
        return result;
    }
    
    private double calculateErrorRate() {
        long errors = errorCounter.sum();
        long total = totalCounter.sum();
        
        return total > 0 ? (double) errors / total : 0.0;
    }
}
```

## 💎 Best Practices Summary

### Do's ✅

1. **Use specific error types** - Create meaningful error hierarchies
2. **Preserve error context** - Include correlation IDs and relevant data
3. **Fail fast when appropriate** - Don't waste resources on doomed operations
4. **Implement circuit breakers** - Protect downstream services
5. **Monitor error patterns** - Use metrics to detect issues early
6. **Test error scenarios** - Include error cases in your test suite
7. **Document error behaviors** - Make error handling explicit in APIs

### Don'ts ❌

1. **Don't ignore errors** - Always handle or explicitly propagate
2. **Don't swallow exceptions** - Preserve error information
3. **Don't use exceptions for control flow** - Use Result/Either patterns
4. **Don't create overly generic errors** - Be specific about failure modes
5. **Don't forget about performance** - Consider allocation overhead
6. **Don't block on error handling** - Keep error processing async when possible
7. **Don't hardcode error messages** - Use internationalization-ready approaches

### Golden Rules 🏆

1. **Make errors explicit** - Use types to represent possible failures
2. **Compose error handling** - Build complex error handling from simple pieces
3. **Separate concerns** - Keep business logic separate from error handling
4. **Design for observability** - Make errors easy to monitor and debug
5. **Plan for degradation** - Always have a fallback strategy
