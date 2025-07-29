# Testing Best Practices 🧪

Comprehensive testing strategies for modern Java exception handling patterns, including unit testing, property-based testing, and chaos engineering approaches.

## 🎯 Testing Philosophy

### Test Pyramid for Error Handling

```
    🔺 E2E Tests (Error Scenarios)
   🔺🔺 Integration Tests (Service Boundaries)  
  🔺🔺🔺 Unit Tests (Pattern Behavior)
 🔺🔺🔺🔺 Property Tests (Edge Cases)
```

### Key Testing Principles

1. **Test both success and failure paths** - Error cases are just as important
2. **Use property-based testing** - Explore edge cases automatically
3. **Mock external failures** - Simulate network timeouts, database errors
4. **Test recovery mechanisms** - Verify fallback and retry behavior
5. **Validate error context** - Ensure error information is preserved
6. **Performance test error paths** - Error handling shouldn't be slow

## 🔍 Unit Testing Patterns

### Testing Result Pattern

```java
@ExtendWith(MockitoExtension.class)
class UserServiceTest {
    
    @Mock
    private UserRepository userRepository;
    
    @InjectMocks
    private UserService userService;
    
    @Test
    @DisplayName("Should return success when user exists")
    void shouldReturnSuccessWhenUserExists() {
        // Given
        String userId = "123";
        User expectedUser = new User(userId, "John Doe");
        when(userRepository.findById(userId)).thenReturn(Optional.of(expectedUser));
        
        // When
        Result<User, UserServiceError> result = userService.findUser(userId);
        
        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getValue()).isEqualTo(expectedUser);
    }
    
    @Test
    @DisplayName("Should return failure when user not found")
    void shouldReturnFailureWhenUserNotFound() {
        // Given
        String userId = "999";
        when(userRepository.findById(userId)).thenReturn(Optional.empty());
        
        // When
        Result<User, UserServiceError> result = userService.findUser(userId);
        
        // Then
        assertThat(result.isFailure()).isTrue();
        assertThat(result.getError()).isEqualTo(UserServiceError.NOT_FOUND);
    }
    
    @Test
    @DisplayName("Should translate database exceptions properly")
    void shouldTranslateDatabaseExceptions() {
        // Given
        String userId = "123";
        when(userRepository.findById(userId))
            .thenThrow(new DataAccessException("Database connection failed") {});
        
        // When
        Result<User, UserServiceError> result = userService.findUser(userId);
        
        // Then
        assertThat(result.isFailure()).isTrue();
        assertThat(result.getError()).isEqualTo(UserServiceError.DATABASE_UNAVAILABLE);
    }
}
```

### Testing Either Pattern

```java
class ValidationTest {
    
    @ParameterizedTest
    @ValueSource(strings = {"", " ", "   "})
    @DisplayName("Should reject blank names")
    void shouldRejectBlankNames(String blankName) {
        Either<String, User> result = UserValidator.validateUser(
            new UserCreateRequest(blankName, 25, "test@example.com")
        );
        
        assertThat(result.isLeft()).isTrue();
        assertThat(result.getLeft()).contains("Name cannot be blank");
    }
    
    @ParameterizedTest
    @ValueSource(ints = {-1, -10, 200, 1000})
    @DisplayName("Should reject invalid ages")
    void shouldRejectInvalidAges(int invalidAge) {
        Either<String, User> result = UserValidator.validateUser(
            new UserCreateRequest("John", invalidAge, "test@example.com")
        );
        
        assertThat(result.isLeft()).isTrue();
        assertThat(result.getLeft()).containsAnyOf("negative", "realistic");
    }
    
    @Test
    @DisplayName("Should accumulate multiple validation errors")
    void shouldAccumulateMultipleValidationErrors() {
        Either<List<String>, User> result = UserValidator.validateUserWithAccumulation(
            new UserCreateRequest("", -5, "invalid-email")
        );
        
        assertThat(result.isLeft()).isTrue();
        List<String> errors = result.getLeft();
        assertThat(errors).hasSize(3);
        assertThat(errors).containsExactlyInAnyOrder(
            "Name cannot be blank",
            "Age must be non-negative", 
            "Invalid email format"
        );
    }
}
```

### Testing Circuit Breaker

```java
class CircuitBreakerTest {
    
    private CircuitBreaker circuitBreaker;
    private MockService mockService;
    
    @BeforeEach
    void setUp() {
        circuitBreaker = CircuitBreaker.builder()
            .name("TestService")
            .failureThreshold(2)
            .successThreshold(2)
            .waitDurationInOpenState(Duration.ofMillis(100))
            .build();
        mockService = mock(MockService.class);
    }
    
    @Test
    @DisplayName("Should open circuit after threshold failures")
    void shouldOpenCircuitAfterThresholdFailures() {
        // Given
        when(mockService.call()).thenThrow(new RuntimeException("Service failure"));
        
        // When - trigger failures
        circuitBreaker.execute(() -> mockService.call());
        circuitBreaker.execute(() -> mockService.call());
        
        // Then
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        
        // Further calls should fail fast
        Result<String, CircuitBreakerException> result = 
            circuitBreaker.execute(() -> mockService.call());
        
        assertThat(result.isFailure()).isTrue();
        assertThat(result.getError().getMessage()).contains("Circuit breaker is OPEN");
        
        // Verify service wasn't called again
        verify(mockService, times(2)).call();
    }
    
    @Test
    @DisplayName("Should transition to half-open after wait duration")
    void shouldTransitionToHalfOpenAfterWaitDuration() throws InterruptedException {
        // Given - open the circuit
        when(mockService.call()).thenThrow(new RuntimeException("Service failure"));
        circuitBreaker.execute(() -> mockService.call());
        circuitBreaker.execute(() -> mockService.call());
        
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        
        // When - wait for transition
        Thread.sleep(150); // Wait longer than waitDurationInOpenState
        when(mockService.call()).thenReturn("Success");
        
        // Then - should allow one call in half-open state
        Result<String, CircuitBreakerException> result = 
            circuitBreaker.execute(() -> mockService.call());
        
        assertThat(result.isSuccess()).isTrue();
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.HALF_OPEN);
    }
}
```

## 🎲 Property-Based Testing

### JQwik Integration

```java
@ExtendWith(JqwikExtension.class)
class ErrorHandlingProperties {
    
    @Property
    @Label("Result should preserve error information through transformations")
    void resultShouldPreserveErrorInformation(
        @ForAll @StringLength(min = 1, max = 100) String originalError,
        @ForAll @IntRange(min = 1, max = 10) int transformationCount) {
        
        // Given
        Result<String, String> initialResult = Result.failure(originalError);
        
        // When - apply multiple transformations
        Result<String, String> finalResult = initialResult;
        for (int i = 0; i < transformationCount; i++) {
            finalResult = finalResult.map(s -> s + "_transformed");
        }
        
        // Then - error should be preserved
        assertThat(finalResult.isFailure()).isTrue();
        assertThat(finalResult.getError()).isEqualTo(originalError);
    }
    
    @Property
    @Label("Either composition should be associative")
    void eitherCompositionShouldBeAssociative(
        @ForAll("validStrings") String input,
        @ForAll Random random) {
        
        Function<String, Either<String, Integer>> f = s -> 
            random.nextBoolean() ? Either.right(s.length()) : Either.left("Error in f");
        
        Function<Integer, Either<String, String>> g = i ->
            random.nextBoolean() ? Either.right("Result: " + i) : Either.left("Error in g");
        
        Function<String, Either<String, Double>> h = s ->
            random.nextBoolean() ? Either.right((double) s.length()) : Either.left("Error in h");
        
        // Test associativity: (f >=> g) >=> h == f >=> (g >=> h)
        Either<String, Double> left = Either.right(input)
            .flatMap(f)
            .flatMap(g)
            .flatMap(h);
        
        Either<String, Double> right = Either.right(input)
            .flatMap(s -> f.apply(s).flatMap(g))
            .flatMap(h);
        
        // Both should have the same result structure
        assertThat(left.isLeft()).isEqualTo(right.isLeft());
        assertThat(left.isRight()).isEqualTo(right.isRight());
    }
    
    @Provide
    Arbitrary<String> validStrings() {
        return Arbitraries.strings()
            .withCharRange('a', 'z')
            .ofMinLength(1)
            .ofMaxLength(50);
    }
}
```

## 🌪️ Chaos Engineering

### Chaos Testing Framework

```java
class ChaosEngineeringTest {
    
    private UserService userService;
    private ChaosConfiguration chaosConfig;
    
    @Test
    @DisplayName("System should remain functional under random failures")
    void shouldRemainFunctionalUnderRandomFailures() {
        // Configure chaos
        chaosConfig.setFailureRate(0.3); // 30% failure rate
        chaosConfig.setLatencyEnabled(true);
        chaosConfig.setLatencyRange(Duration.ofMillis(100), Duration.ofSeconds(2));
        
        int totalRequests = 1000;
        int successCount = 0;
        int failureCount = 0;
        List<Duration> responseTimes = new ArrayList<>();
        
        for (int i = 0; i < totalRequests; i++) {
            long startTime = System.nanoTime();
            
            Result<User, UserServiceError> result = userService.findUser("user" + i);
            
            long endTime = System.nanoTime();
            responseTimes.add(Duration.ofNanos(endTime - startTime));
            
            if (result.isSuccess()) {
                successCount++;
            } else {
                failureCount++;
            }
        }
        
        // Assertions for system resilience
        double successRate = (double) successCount / totalRequests;
        assertThat(successRate).isGreaterThan(0.6); // At least 60% success rate
        
        Duration avgResponseTime = responseTimes.stream()
            .reduce(Duration.ZERO, Duration::plus)
            .dividedBy(responseTimes.size());
        
        assertThat(avgResponseTime).isLessThan(Duration.ofSeconds(5)); // Reasonable response time
        
        // Verify graceful degradation
        assertThat(failureCount).isGreaterThan(0); // Some failures should occur
        assertThat(successCount).isGreaterThan(0); // But system should still work
    }
}

public class ChaosConfiguration {
    private double failureRate = 0.0;
    private boolean latencyEnabled = false;
    private Duration minLatency = Duration.ZERO;
    private Duration maxLatency = Duration.ZERO;
    
    public UserRepository createChaosUserRepository(UserRepository delegate) {
        return new ChaosUserRepository(delegate, this);
    }
    
    // Getters and setters...
}

class ChaosUserRepository implements UserRepository {
    private final UserRepository delegate;
    private final ChaosConfiguration config;
    private final Random random = new Random();
    
    @Override
    public Optional<User> findById(String id) {
        injectChaos();
        return delegate.findById(id);
    }
    
    private void injectChaos() {
        // Inject random failures
        if (random.nextDouble() < config.getFailureRate()) {
            throw new DataAccessException("Chaos-induced failure") {};
        }
        
        // Inject random latency
        if (config.isLatencyEnabled()) {
            Duration latency = Duration.ofMillis(
                config.getMinLatency().toMillis() + 
                random.nextLong(config.getMaxLatency().toMillis() - config.getMinLatency().toMillis())
            );
            
            try {
                Thread.sleep(latency.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
```

## 🔄 Integration Testing

### Testing Error Boundaries

```java
class ErrorBoundaryIntegrationTest {
    
    private TestRestTemplate restTemplate;
    private ExternalApiClient externalApiClient;
    
    @Test
    @Order(1)
    @DisplayName("Should handle external service failures gracefully")
    void shouldHandleExternalServiceFailuresGracefully() {
        // Given
        when(externalApiClient.fetchUserData(anyString()))
            .thenThrow(new ConnectException("Service unavailable"));
        
        // When
        Result<User, String> result = userService.getUser("123");
        
        // Then
        assertThat(result.isFailure()).isTrue();
        assertThat(result.getError()).contains("External service unavailable");
    }
    
    @Test
    @Order(2)
    @DisplayName("Should recover with fallback data")
    void shouldRecoverWithFallbackData() {
        // Given
        when(externalApiClient.fetchUserData(anyString()))
            .thenThrow(new SocketTimeoutException("Timeout"));
        
        // When
        Result<User, String> result = userService.getUserWithFallback("123");
        
        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getValue().getName()).isEqualTo("Fallback User");
    }
}
```

### Database Transaction Testing

```java
class TransactionalErrorHandlingTest {
    
    private TestEntityManager entityManager;
    private UserRepository userRepository;
    
    @Test
    @DisplayName("Should rollback transaction on validation failure")
    void shouldRollbackTransactionOnValidationFailure() {
        // Given
        User validUser = new User("john@example.com", "John Doe");
        User invalidUser = new User("", ""); // Invalid data
        
        // When
        Result<List<User>, Exception> result = Result.of(() -> {
            userRepository.save(validUser);
            userRepository.save(invalidUser); // This should fail
            entityManager.flush(); // Force validation
            return List.of(validUser, invalidUser);
        });
        
        // Then
        assertThat(result.isFailure()).isTrue();
        
        // Verify transaction was rolled back
        List<User> users = userRepository.findAll();
        assertThat(users).isEmpty();
    }
}
```

## 📊 Performance Testing

### Load Testing Error Scenarios

```java
class ErrorHandlingPerformanceTest {
    
    private UserService userService;
    
    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    @DisplayName("Error handling should not degrade performance significantly")
    void errorHandlingShouldNotDegradePerformanceSignificantly() throws InterruptedException {
        int threadCount = 50;
        int requestsPerThread = 1000;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger errorCount = new AtomicInteger();
        AtomicLong totalResponseTime = new AtomicLong();
        
        // Submit concurrent requests
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    for (int j = 0; j < requestsPerThread; j++) {
                        long startTime = System.nanoTime();
                        
                        // Mix of valid and invalid requests
                        String userId = (j % 10 == 0) ? "invalid" : "user" + (threadId * 1000 + j);
                        Result<User, UserServiceError> result = userService.findUser(userId);
                        
                        long endTime = System.nanoTime();
                        totalResponseTime.addAndGet(endTime - startTime);
                        
                        if (result.isSuccess()) {
                            successCount.incrementAndGet();
                        } else {
                            errorCount.incrementAndGet();
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
        
        // Wait for completion
        assertThat(latch.await(25, TimeUnit.SECONDS)).isTrue();
        executor.shutdown();
        
        // Performance assertions
        int totalRequests = threadCount * requestsPerThread;
        double avgResponseTimeMs = totalResponseTime.get() / 1_000_000.0 / totalRequests;
        
        assertThat(avgResponseTimeMs).isLessThan(10.0); // Average response time < 10ms
        assertThat(successCount.get() + errorCount.get()).isEqualTo(totalRequests);
        assertThat(errorCount.get()).isGreaterThan(0); // Some errors should occur
    }
}
```

## 🎯 Test Organization

### Test Categories

```java
// Marker interfaces for test organization
public interface UnitTest {}
public interface IntegrationTest {}
public interface PerformanceTest {}
public interface ChaosTest {}

// Usage
@Tag("unit")
class ResultPatternTest implements UnitTest {
    // Unit tests here
}

@Tag("integration") 
class ServiceBoundaryTest implements IntegrationTest {
    // Integration tests here
}

@Tag("performance")
class LoadTest implements PerformanceTest {
    // Performance tests here
}
```

### Test Suites

```java
// Gradle configuration for test suites
test {
    useJUnitPlatform {
        includeTags 'unit'
    }
}

task integrationTest(type: Test) {
    useJUnitPlatform {
        includeTags 'integration'
    }
    testClassesDirs = sourceSets.test.output.classesDirs
    classpath = sourceSets.test.runtimeClasspath
}

task performanceTest(type: Test) {
    useJUnitPlatform {
        includeTags 'performance'
    }
    testClassesDirs = sourceSets.test.output.classesDirs
    classpath = sourceSets.test.runtimeClasspath
}
```

## 💡 Testing Best Practices

### 1. Test Data Builders

```java
public class UserTestDataBuilder {
    private String email = "test@example.com";
    private String name = "Test User";
    private int age = 25;
    
    public static UserTestDataBuilder aUser() {
        return new UserTestDataBuilder();
    }
    
    public UserTestDataBuilder withEmail(String email) {
        this.email = email;
        return this;
    }
    
    public UserTestDataBuilder withInvalidEmail() {
        this.email = "invalid-email";
        return this;
    }
    
    public UserTestDataBuilder withAge(int age) {
        this.age = age;
        return this;
    }
    
    public UserTestDataBuilder withInvalidAge() {
        this.age = -1;
        return this;
    }
    
    public User build() {
        return new User(email, name, age);
    }
    
    public UserCreateRequest buildRequest() {
        return new UserCreateRequest(name, age, email);
    }
}
```

### 2. Custom Assertions

```java
public class ResultAssertions extends AbstractAssert<ResultAssertions, Result<?, ?>> {
    
    public ResultAssertions(Result<?, ?> actual) {
        super(actual, ResultAssertions.class);
    }
    
    public static ResultAssertions assertThat(Result<?, ?> actual) {
        return new ResultAssertions(actual);
    }
    
    public ResultAssertions isSuccess() {
        isNotNull();
        if (!actual.isSuccess()) {
            failWithMessage("Expected result to be success but was failure with error: %s", 
                           actual.getError());
        }
        return this;
    }
    
    public ResultAssertions isFailure() {
        isNotNull();
        if (!actual.isFailure()) {
            failWithMessage("Expected result to be failure but was success with value: %s", 
                           actual.getValue());
        }
        return this;
    }
    
    public ResultAssertions hasErrorOfType(Class<? extends Exception> expectedType) {
        isFailure();
        if (!expectedType.isInstance(actual.getError())) {
            failWithMessage("Expected error of type %s but was %s", 
                           expectedType.getSimpleName(), 
                           actual.getError().getClass().getSimpleName());
        }
        return this;
    }
}
```

Remember: **Test your error handling as thoroughly as your happy paths!** 🎯
