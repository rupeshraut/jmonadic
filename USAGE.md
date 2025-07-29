# 📚 Using JMonadic as a Library

JMonadic is a lightweight functional programming library that provides robust monadic patterns for Java applications.

## 📦 **Installation**

### **🎯 Core Module**

**For Gradle projects:**
```gradle
dependencies {
    implementation 'org.jmonadic:jmonadic-core:1.0.0'
}
```

**For Maven projects:**
```xml
<dependency>
    <groupId>org.jmonadic</groupId>
    <artifactId>jmonadic-core</artifactId>
    <version>1.0.0</version>
</dependency>
```

## **Basic Usage Examples**

#### **Core Patterns**
```java
import org.jmonadic.patterns.Result;
import org.jmonadic.patterns.Either;
import org.jmonadic.patterns.Try;
import org.jmonadic.patterns.Option;
import org.jmonadic.patterns.Validation;

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
    
    // Option pattern for null safety
    public Option<User> findOptionalUser(Long id) {
        return Option.ofNullable(userRepository.findById(id));
    }
    
    // Validation pattern for error accumulation
    public Validation<String, User> validateUserData(User user) {
        return Validation.of(user)
            .ensure(u -> u.getEmail() != null, "Email is required")
            .ensure(u -> u.getAge() >= 18, "Must be 18 or older")
            .ensure(u -> u.getName() != null, "Name is required");
    }
}
```

#### **Resilience Patterns**
```java
import org.jmonadic.resilience.CircuitBreaker;
import org.jmonadic.resilience.RetryPolicy;

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
import org.jmonadic.performance.FastFailResult;
import org.jmonadic.performance.ZeroAllocationException;

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

#### **Option Pattern for Null Safety**
```java
import org.jmonadic.patterns.Option;

public class UserService {
    
    public Option<User> findUserByEmail(String email) {
        User user = userRepository.findByEmail(email);
        return Option.ofNullable(user);
    }
    
    public Result<String, String> getUserDisplayName(String email) {
        return findUserByEmail(email)
            .map(user -> user.getFirstName() + " " + user.getLastName())
            .toResult(() -> "User not found with email: " + email);
    }
}
```

#### **Validation Pattern for Error Accumulation**
```java
import org.jmonadic.patterns.Validation;

public class UserValidator {
    
    public Validation<String, User> validateUser(CreateUserRequest request) {
        return Validation.of(request)
            .ensure(r -> r.getName() != null && !r.getName().trim().isEmpty(), "Name is required")
            .ensure(r -> r.getEmail() != null && r.getEmail().contains("@"), "Valid email is required")
            .ensure(r -> r.getAge() >= 18, "Must be 18 or older")
            .map(r -> new User(r.getName(), r.getEmail(), r.getAge()));
    }
}
```

### **Step 3: Configuration**

Create resilience configuration:

```java
import org.jmonadic.resilience.CircuitBreaker;
import org.jmonadic.resilience.RetryPolicy;
import java.time.Duration;

public class JMonadicConfig {
    
    public static CircuitBreaker defaultCircuitBreaker() {
        return CircuitBreaker.builder()
            .name("DefaultService")
            .failureThreshold(5)
            .waitDurationInOpenState(Duration.ofSeconds(30))
            .build();
    }
    
    public static RetryPolicy defaultRetryPolicy() {
        return RetryPolicy.builder()
            .maxAttempts(3)
            .initialDelay(Duration.ofMillis(100))
            .backoffMultiplier(2.0)
            .build();
    }
}
```

## 🔧 **Method 2: Copy Core Classes**

If you prefer to copy specific classes, here are the essential ones:

### **Minimal Setup (Core Patterns Only)**
```
org.jmonadic.patterns.Result
org.jmonadic.patterns.Either  
org.jmonadic.patterns.Try
org.jmonadic.patterns.Option
org.jmonadic.patterns.Validation
org.jmonadic.utils.MonadicUtils
```

### **Resilience Setup**
```
+ org.jmonadic.resilience.CircuitBreaker
+ org.jmonadic.resilience.RetryPolicy
```

### **Performance Setup**
```
+ org.jmonadic.performance.FastFailResult
+ org.jmonadic.performance.ZeroAllocationException
```

### **Observability Setup**
```
+ org.jmonadic.testing.ChaosEngineering
```

## 📦 **Method 3: Fat JAR Distribution**

Build a fat JAR for easy distribution:

```bash
./gradlew shadowJar
```

Then include the generated JAR in your project's `libs` folder and add:

```gradle
dependencies {
    implementation files('libs/jmonadic-1.0.0-fat.jar')
}
```

## 🧪 **Method 4: Git Submodule**

Add as a Git submodule:

```bash
git submodule add https://github.com/rupeshraut/jmonadic.git libs/jmonadic
```

Then in your `build.gradle`:
```gradle
dependencies {
    implementation project(':libs:jmonadic')
}
```

## 📋 **Usage Patterns**

### **1. Service Layer Pattern**
```java
public class UserService {
    
    public Result<User, UserError> createUser(CreateUserRequest request) {
        return validateRequest(request)
            .flatMap(this::checkDuplicateEmail)
            .flatMap(this::saveUser)
            .peekSuccess(user -> logger.info("User created: {}", user.getId()))
            .peekError(error -> logger.error("User creation failed: {}", error));
    }
    
    private Result<CreateUserRequest, UserError> validateRequest(CreateUserRequest request) {
        return MonadicUtils.Validation.requireEmail(request.getEmail())
            .mapError(msg -> new UserError("INVALID_EMAIL", msg))
            .map(email -> request);
    }
}
```

### **2. Controller Layer Pattern**
```java
public class UserController {
    
    public ResponseEntity<ApiResponse<User>> createUser(CreateUserRequest request) {
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
import org.jmonadic.testing.ChaosEngineering;

public class ChaosTestConfig {
    
    public static ChaosEngineering chaosEngineering() {
        return ChaosEngineering.builder()
            .enabled(true)
            .failureProbability(0.1) // 10% chaos injection
            .build();
    }
}
```

## 📊 **Monitoring and Observability**

JMonadic provides several observability features:

- **Circuit breaker metrics** - State transitions and failure rates
- **Retry policy metrics** - Attempt counts and success rates  
- **Performance optimization** - FastFailResult for high-frequency operations
- **Chaos engineering** - Controlled failure injection for testing

Access metrics through the built-in monitoring capabilities of each component.

## 🔍 **Best Practices**

1. **Use Result for operations that can fail** - Replace throws with Result returns
2. **Chain operations with flatMap** - Build pipelines of fallible operations  
3. **Use Either for simple validation** - Binary success/failure scenarios
4. **Use Validation for complex validation** - Accumulate multiple validation errors
5. **Use Option for null safety** - Replace null checks with type-safe operations
6. **Use Try for exception-prone operations** - Wrap risky operations safely
7. **Wrap external calls with resilience** - Always use CircuitBreaker + RetryPolicy
8. **Use FastFailResult for performance** - Pre-allocated instances for hot paths

## 📈 **Performance Considerations**

- **FastFailResult**: Pre-allocated instances for common success/failure patterns
- **ZeroAllocationException**: Zero-allocation exceptions for performance-critical paths  
- **MonadicUtils**: Utility methods for sequence/traverse operations on collections
- **Result caching**: Consider caching Result instances for repeated operations
- **Async operations**: Combine with CompletableFuture for non-blocking patterns
- **Chaos Engineering**: Test performance under controlled failure conditions

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