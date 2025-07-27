# 📚 Using Exception Handling Toolkit as a Library

The Exception Handling Toolkit is available as a modular library with separate core and Spring integration modules for flexible usage.

## 📦 **Module Selection**

### **🎯 Core Module Only (Recommended for non-Spring projects)**

**For Gradle projects:**
```gradle
dependencies {
    implementation 'com.exception.showcase:exception-core:1.0.0'
}
```

**For Maven projects:**
```xml
<dependency>
    <groupId>com.exception.showcase</groupId>
    <artifactId>exception-core</artifactId>
    <version>1.0.0</version>
</dependency>
```

### **🌱 Spring Integration Module (Includes core automatically)**

**For Gradle projects:**
```gradle
dependencies {
    implementation 'com.exception.showcase:exception-spring:1.0.0'
    // exception-core is included automatically as a dependency
}
```

**For Maven projects:**
```xml
<dependency>
    <groupId>com.exception.showcase</groupId>
    <artifactId>exception-spring</artifactId>
    <version>1.0.0</version>
</dependency>
```

### **Step 2: Basic Usage Examples**

#### **Core Patterns**
```java
import com.exception.showcase.patterns.Result;
import com.exception.showcase.patterns.Either;
import com.exception.showcase.patterns.Try;

// Result pattern for functional error handling
public class UserService {
    
    public Result<User, String> findUser(Long id) {
        return Result.of(() -> userRepository.findById(id))
            .flatMap(optUser -> optUser.isPresent() 
                ? Result.success(optUser.get())
                : Result.failure("User not found"))
            .map(this::enrichUserData);
    }
    
    // Either pattern for validation
    public Either<ValidationError, User> validateUser(User user) {
        if (user.getEmail() == null) {
            return Either.left(new ValidationError("Email required"));
        }
        if (user.getAge() < 18) {
            return Either.left(new ValidationError("Must be 18+"));
        }
        return Either.right(user);
    }
    
    // Try pattern for safe operations
    public Try<Integer> parseAge(String ageStr) {
        return Try.of(() -> Integer.parseInt(ageStr))
            .recover(ex -> 0); // Default age on parse error
    }
}
```

#### **Resilience Patterns**
```java
import com.exception.showcase.resilience.CircuitBreaker;
import com.exception.showcase.resilience.RetryPolicy;

@Service
public class ExternalApiService {
    
    private final CircuitBreaker circuitBreaker = CircuitBreaker.builder()
        .name("ExternalAPI")
        .failureThreshold(5)
        .waitDurationInOpenState(Duration.ofSeconds(30))
        .build();
    
    private final RetryPolicy retryPolicy = RetryPolicy.builder()
        .maxAttempts(3)
        .initialDelay(Duration.ofMillis(100))
        .backoffMultiplier(2.0)
        .build();
    
    public Result<ApiResponse, Exception> callExternalApi(String endpoint) {
        return retryPolicy.executeWithCircuitBreaker(() -> {
            // Your API call logic
            return restTemplate.getForObject(endpoint, ApiResponse.class);
        }, circuitBreaker);
    }
}
```

#### **Performance Optimizations**
```java
import com.exception.showcase.performance.FastFailResult;
import com.exception.showcase.performance.ZeroAllocationException;

public class HighPerformanceService {
    
    // Use pre-allocated results for common cases
    public Result<String, String> quickValidation(String input) {
        if (input == null) {
            return FastFailResult.validationError();
        }
        if (input.isEmpty()) {
            return FastFailResult.emptySuccess();
        }
        return Result.success(input.toUpperCase());
    }
    
    // Zero-allocation exceptions for hot paths
    public void criticalPath(String data) throws ZeroAllocationException {
        if (data == null) {
            ZeroAllocationException ex = ZeroAllocationException.of(
                "NULL_DATA", 
                "Data cannot be null",
                ZeroAllocationException.context()
                    .withOperation("critical_path")
                    .build(),
                false // No stack trace for performance
            );
            throw ex;
        }
    }
}
```

#### **Observability Integration**
```java
import com.exception.showcase.observability.StructuredLogger;
import com.exception.showcase.observability.ExceptionMetrics;

@Service
public class ObservableService {
    
    private final StructuredLogger logger = StructuredLogger.builder()
        .component("UserService")
        .withApplication("my-app")
        .build();
    
    @Autowired
    private ExceptionMetrics metrics;
    
    public Result<User, Exception> processUser(User user) {
        StructuredLogger.LogContext context = StructuredLogger.LogContext.create()
            .withUserId(user.getId().toString())
            .withOperation("process_user");
        
        return logger.wrap("processUser")
            .withContext(context)
            .executeResult(() -> {
                // Your business logic
                return Result.success(processUserLogic(user));
            });
    }
}
```

### **Step 3: Spring Boot Configuration**

Create a configuration class:

```java
@Configuration
@EnableConfigurationProperties
public class ExceptionToolkitConfig {
    
    @Bean
    @ConditionalOnMissingBean
    public MeterRegistry meterRegistry() {
        return new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
    }
    
    @Bean
    public ExceptionMetrics exceptionMetrics(MeterRegistry meterRegistry) {
        return ExceptionMetrics.builder()
            .meterRegistry(meterRegistry)
            .applicationName("my-application")
            .version("1.0.0")
            .build();
    }
    
    @Bean
    public CircuitBreaker defaultCircuitBreaker() {
        return CircuitBreaker.builder()
            .name("DefaultService")
            .failureThreshold(5)
            .waitDurationInOpenState(Duration.ofSeconds(30))
            .build();
    }
}
```

## 🔧 **Method 2: Copy Core Classes**

If you prefer to copy specific classes, here are the essential ones:

### **Minimal Setup (Core Patterns Only)**
```
com.exception.showcase.patterns.Result
com.exception.showcase.patterns.Either  
com.exception.showcase.patterns.Try
com.exception.showcase.utils.ExceptionUtils
```

### **Resilience Setup**
```
+ com.exception.showcase.resilience.CircuitBreaker
+ com.exception.showcase.resilience.RetryPolicy
```

### **Performance Setup**
```
+ com.exception.showcase.performance.FastFailResult
+ com.exception.showcase.performance.ZeroAllocationException
```

### **Observability Setup**
```
+ com.exception.showcase.observability.StructuredLogger
+ com.exception.showcase.observability.ExceptionMetrics (requires Micrometer)
```

## 📦 **Method 3: Fat JAR Distribution**

Build a fat JAR for easy distribution:

```bash
./gradlew shadowJar
```

Then include the generated JAR in your project's `libs` folder and add:

```gradle
dependencies {
    implementation files('libs/java-exception-showcase-1.0.0-fat.jar')
}
```

## 🧪 **Method 4: Git Submodule**

Add as a Git submodule:

```bash
git submodule add https://github.com/your-org/java-exception-toolkit libs/exception-toolkit
```

Then in your `build.gradle`:
```gradle
dependencies {
    implementation project(':libs:exception-toolkit')
}
```

## 📋 **Usage Patterns**

### **1. Service Layer Pattern**
```java
@Service
public class UserService {
    
    public Result<User, UserError> createUser(CreateUserRequest request) {
        return validateRequest(request)
            .flatMap(this::checkDuplicateEmail)
            .flatMap(this::saveUser)
            .peekSuccess(user -> logger.info("User created: {}", user.getId()))
            .peekError(error -> logger.error("User creation failed: {}", error));
    }
    
    private Result<CreateUserRequest, UserError> validateRequest(CreateUserRequest request) {
        return ExceptionUtils.Validation.requireEmail(request.getEmail())
            .mapError(msg -> new UserError("INVALID_EMAIL", msg))
            .map(email -> request);
    }
}
```

### **2. Controller Layer Pattern**
```java
@RestController
public class UserController {
    
    @PostMapping("/users")
    public ResponseEntity<ApiResponse<User>> createUser(@RequestBody CreateUserRequest request) {
        return userService.createUser(request)
            .fold(
                user -> ResponseEntity.ok(ApiResponse.success(user)),
                error -> ResponseEntity.badRequest()
                    .body(ApiResponse.error(error.getCode(), error.getMessage()))
            );
    }
}
```

### **3. Testing with Chaos Engineering**
```java
@TestConfiguration
public class ChaosTestConfig {
    
    @Bean
    @Primary
    public ChaosEngineering chaosEngineering() {
        return ChaosEngineering.builder()
            .enabled(true)
            .failureProbability(0.1) // 10% chaos injection
            .build();
    }
}
```

## 📊 **Monitoring and Metrics**

The toolkit automatically provides metrics for:

- **Success/failure rates** by operation
- **Circuit breaker states** and transitions  
- **Retry attempt counts** and success rates
- **Performance timings** and latency percentiles

Access metrics at `/actuator/prometheus` (Spring Boot) or through your MeterRegistry.

## 🔍 **Best Practices**

1. **Use Result for operations that can fail** - Replace throws with Result returns
2. **Chain operations with flatMap** - Build pipelines of fallible operations
3. **Use Either for validation** - Accumulate validation errors
4. **Wrap external calls with resilience** - Always use CircuitBreaker + RetryPolicy
5. **Log with structured context** - Include correlation IDs and operation metadata
6. **Monitor everything** - Use ExceptionMetrics for observability

## 📈 **Performance Considerations**

- **FastFailResult**: Use for high-frequency operations
- **ZeroAllocationException**: Use in performance-critical paths
- **Result caching**: Consider caching Result instances for repeated operations
- **Async operations**: Combine with CompletableFuture for non-blocking patterns

## 🤝 **Migration Guide**

**From Traditional Exception Handling:**
```java
// Before
public User getUser(Long id) throws UserNotFoundException {
    User user = userRepository.findById(id);
    if (user == null) {
        throw new UserNotFoundException("User not found: " + id);
    }
    return user;
}

// After  
public Result<User, String> getUser(Long id) {
    return Result.of(() -> userRepository.findById(id))
        .flatMap(user -> user != null 
            ? Result.success(user)
            : Result.failure("User not found: " + id));
}
```

This library transforms your error handling from imperative exceptions to functional, composable patterns while adding production-ready resilience and observability! 🚀