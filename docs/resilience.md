# Resilience Strategies 🛡️

Modern applications must be resilient to failures. This guide covers proven patterns for building fault-tolerant systems using circuit breakers, retry mechanisms, and other resilience strategies.

## 🔌 Circuit Breaker Pattern

The Circuit Breaker pattern prevents cascading failures by monitoring service calls and temporarily blocking requests when failures exceed a threshold.

### States and Transitions

```
CLOSED ──(failures ≥ threshold)──> OPEN
   ↑                                 │
   │                                 │
   └──(success count ≥ threshold)─── HALF_OPEN ←─(timeout elapsed)─┘
```

### Basic Usage

```java
CircuitBreaker circuitBreaker = CircuitBreaker.builder()
    .name("PaymentService")
    .failureThreshold(5)                    // Open after 5 failures
    .successThreshold(3)                    // Close after 3 successes in half-open
    .waitDurationInOpenState(Duration.ofSeconds(30))  // Wait 30s before trying
    .timeout(Duration.ofSeconds(2))         // Individual call timeout
    .build();

// Protected service call
Result<Payment, CircuitBreakerException> result = 
    circuitBreaker.execute(() -> paymentService.processPayment(request));
```

### Advanced Configuration

```java
CircuitBreaker advancedCircuitBreaker = CircuitBreaker.builder()
    .name("DatabaseService")
    .failureThreshold(10)
    .successThreshold(5)
    .waitDurationInOpenState(Duration.ofMinutes(1))
    .timeout(Duration.ofSeconds(5))
    .build();

// With custom error handling
Result<Data, CircuitBreakerException> result = advancedCircuitBreaker
    .execute(() -> databaseService.fetchData(query))
    .recover(error -> fallbackDataService.fetchData(query))
    .peekError(error -> alertingService.notifyFailure(error));
```

## 🔄 Retry Patterns

Retry mechanisms handle transient failures by automatically retrying operations with configurable backoff strategies.

### Exponential Backoff with Jitter

```java
RetryPolicy retryPolicy = RetryPolicy.builder()
    .name("HttpClient")
    .maxAttempts(5)
    .initialDelay(Duration.ofMillis(100))
    .maxDelay(Duration.ofSeconds(10))
    .backoffMultiplier(2.0)               // 100ms, 200ms, 400ms, 800ms, 1600ms
    .jitterFactor(0.2)                    // ±20% randomization
    .retryIf(ex -> ex instanceof SocketTimeoutException ||
                   ex instanceof ConnectException)
    .build();

Result<Response, Exception> response = retryPolicy.execute(() -> 
    httpClient.get(url));
```

### Async Retry

```java
CompletableFuture<Result<Data, Exception>> asyncResult = 
    retryPolicy.executeAsync(() -> externalService.fetchData());

asyncResult.thenAccept(result -> {
    if (result.isSuccess()) {
        processData(result.getValue());
    } else {
        handleFailure(result.getError());
    }
});
```

### Preset Configurations

```java
// Quick retry for fast operations
RetryPolicy quickRetry = RetryPolicy.Presets.quickRetry();

// Resilient retry for critical operations  
RetryPolicy resilientRetry = RetryPolicy.Presets.resilientRetry();

// Network-specific retry
RetryPolicy networkRetry = RetryPolicy.Presets.networkRetry();
```

## 🔗 Combining Patterns

### Circuit Breaker + Retry

```java
CircuitBreaker circuitBreaker = CircuitBreaker.builder()
    .name("ExternalAPI")
    .failureThreshold(3)
    .build();

RetryPolicy retryPolicy = RetryPolicy.builder()
    .maxAttempts(3)
    .initialDelay(Duration.ofMillis(500))
    .build();

// Retry with circuit breaker protection
Result<ApiResponse, Exception> result = retryPolicy
    .executeWithCircuitBreaker(() -> apiClient.call(), circuitBreaker);
```

### Bulkhead Pattern

```java
public class ServiceBulkhead {
    private final ExecutorService criticalPool = 
        Executors.newFixedThreadPool(10);
    private final ExecutorService nonCriticalPool = 
        Executors.newFixedThreadPool(5);
    
    public CompletableFuture<Result<Data, Exception>> criticalOperation() {
        return CompletableFuture.supplyAsync(() -> 
            Result.of(() -> criticalService.process()), criticalPool);
    }
    
    public CompletableFuture<Result<Data, Exception>> nonCriticalOperation() {
        return CompletableFuture.supplyAsync(() -> 
            Result.of(() -> nonCriticalService.process()), nonCriticalPool);
    }
}
```

## 📊 Monitoring and Observability

### Metrics Collection

```java
public class CircuitBreakerMetrics {
    private final MeterRegistry meterRegistry;
    private final Counter failureCounter;
    private final Timer executionTimer;
    private final Gauge stateGauge;
    
    public CircuitBreakerMetrics(CircuitBreaker circuitBreaker, MeterRegistry registry) {
        this.meterRegistry = registry;
        String name = circuitBreaker.getName();
        
        this.failureCounter = Counter.builder("circuit_breaker_failures_total")
            .tag("circuit_breaker", name)
            .register(registry);
            
        this.executionTimer = Timer.builder("circuit_breaker_execution_duration")
            .tag("circuit_breaker", name)
            .register(registry);
            
        this.stateGauge = Gauge.builder("circuit_breaker_state")
            .tag("circuit_breaker", name)
            .register(registry, circuitBreaker, cb -> cb.getState().ordinal());
    }
    
    public <T> Result<T, CircuitBreakerException> executeWithMetrics(
            CircuitBreaker circuitBreaker, Supplier<T> operation) {
        
        Timer.Sample sample = Timer.start(meterRegistry);
        
        try {
            Result<T, CircuitBreakerException> result = circuitBreaker.execute(operation);
            
            if (result.isFailure()) {
                failureCounter.increment();
            }
            
            return result;
        } finally {
            sample.stop(executionTimer);
        }
    }
}
```

### Health Checks

```java
@Component
public class ResilienceHealthIndicator implements HealthIndicator {
    private final List<CircuitBreaker> circuitBreakers;
    
    @Override
    public Health health() {
        Health.Builder builder = Health.up();
        
        for (CircuitBreaker cb : circuitBreakers) {
            CircuitBreaker.Metrics metrics = cb.getMetrics();
            
            builder.withDetail(cb.getName() + "_state", metrics.state())
                   .withDetail(cb.getName() + "_failures", metrics.failureCount());
            
            if (metrics.state() == CircuitBreaker.State.OPEN) {
                builder.down();
            }
        }
        
        return builder.build();
    }
}
```

## 🎯 Best Practices

### 1. Graceful Degradation

```java
public class UserService {
    private final CircuitBreaker userDbCircuitBreaker;
    private final CircuitBreaker cacheCircuitBreaker;
    
    public Result<User, ServiceException> getUser(String userId) {
        // Try primary database
        return userDbCircuitBreaker.execute(() -> userRepository.findById(userId))
            .mapError(error -> new ServiceException("Database unavailable", error))
            // Fallback to cache
            .recover(error -> cacheCircuitBreaker.execute(() -> userCache.get(userId))
                .mapError(cacheError -> new ServiceException("Cache unavailable", cacheError)))
            // Final fallback to basic user info
            .recoverWith(error -> createBasicUser(userId));
    }
    
    private User createBasicUser(String userId) {
        return new User(userId, "Guest User", UserStatus.LIMITED);
    }
}
```

### 2. Configuration Management

```java
@ConfigurationProperties(prefix = "resilience")
public class ResilienceConfig {
    private CircuitBreakerConfig circuitBreaker = new CircuitBreakerConfig();
    private RetryConfig retry = new RetryConfig();
    
    public static class CircuitBreakerConfig {
        private int failureThreshold = 5;
        private int successThreshold = 3;
        private Duration waitDuration = Duration.ofSeconds(30);
        private Duration timeout = Duration.ofSeconds(2);
        
        // getters and setters
    }
    
    public static class RetryConfig {
        private int maxAttempts = 3;
        private Duration initialDelay = Duration.ofMillis(100);
        private Duration maxDelay = Duration.ofSeconds(30);
        private double backoffMultiplier = 2.0;
        
        // getters and setters
    }
}
```

### 3. Testing Resilience

```java
@Test
void shouldOpenCircuitBreakerAfterFailures() {
    CircuitBreaker cb = CircuitBreaker.builder()
        .failureThreshold(2)
        .build();
    
    // Trigger failures
    cb.execute(() -> { throw new RuntimeException("Failure 1"); });
    cb.execute(() -> { throw new RuntimeException("Failure 2"); });
    
    assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);
    
    // Subsequent calls should fail fast
    Result<String, CircuitBreakerException> result = 
        cb.execute(() -> "Should not execute");
    
    assertThat(result.isFailure()).isTrue();
    assertThat(result.getError().getMessage()).contains("Circuit breaker is OPEN");
}

@Test
void shouldRetryWithExponentialBackoff() {
    AtomicInteger attempts = new AtomicInteger(0);
    RetryPolicy retry = RetryPolicy.builder()
        .maxAttempts(3)
        .initialDelay(Duration.ofMillis(10))
        .build();
    
    long startTime = System.currentTimeMillis();
    
    Result<String, Exception> result = retry.execute(() -> {
        int attempt = attempts.incrementAndGet();
        if (attempt < 3) {
            throw new RuntimeException("Attempt " + attempt);
        }
        return "Success";
    });
    
    long duration = System.currentTimeMillis() - startTime;
    
    assertThat(result.isSuccess()).isTrue();
    assertThat(attempts.get()).isEqualTo(3);
    assertThat(duration).isGreaterThan(30); // At least 10ms + 20ms delays
}
```

## 🚀 Production Deployment

### 1. Circuit Breaker Tuning

- **Failure Threshold**: Start with 5-10 failures
- **Success Threshold**: Use 2-3 successes for recovery
- **Wait Duration**: Begin with 30-60 seconds
- **Timeout**: Set based on 99th percentile response time

### 2. Retry Configuration

- **Max Attempts**: Usually 3-5 attempts
- **Initial Delay**: 100-500ms for most services
- **Max Delay**: 10-30 seconds maximum
- **Jitter**: 10-30% to prevent thundering herd

### 3. Monitoring Alerts

```yaml
# Prometheus alerting rules
groups:
  - name: circuit_breaker
    rules:
      - alert: CircuitBreakerOpen
        expr: circuit_breaker_state == 1
        for: 1m
        labels:
          severity: warning
        annotations:
          summary: "Circuit breaker {{ $labels.circuit_breaker }} is open"
          
      - alert: HighFailureRate
        expr: rate(circuit_breaker_failures_total[5m]) > 0.1
        for: 2m
        labels:
          severity: critical
        annotations:
          summary: "High failure rate for {{ $labels.circuit_breaker }}"
```

## 💡 Pro Tips

1. **Start Simple**: Begin with basic configurations and tune based on metrics
2. **Monitor Everything**: Track state changes, failure rates, and recovery times
3. **Test Failure Scenarios**: Use chaos engineering to validate resilience
4. **Document Behavior**: Clearly communicate fallback strategies to consumers
5. **Regular Review**: Periodically review and adjust thresholds based on traffic patterns
