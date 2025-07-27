# 🚀 JMonadic Integration Example

This is a complete example showing how to use JMonadic as a library in your Spring Boot application.

## 📋 What This Example Demonstrates

- **Result Monad** for functional error handling in REST APIs
- **Either Monad** for validation with detailed error messages  
- **Circuit Breaker** for resilience against external service failures
- **Structured Logging** with correlation IDs and context
- **Metrics Integration** with Spring Boot Actuator

## 🛠️ Setup

1. **Ensure JMonadic is in your local Maven repository:**
   ```bash
   cd ../
   ./gradlew publishAllToMavenLocal
   ```

2. **Build and run this example:**
   ```bash
   cd example-integration
   ./gradlew bootRun
   ```

3. **Test the endpoints:**

   **Get User (with circuit breaker):**
   ```bash
   curl http://localhost:8080/api/users/1
   ```

   **Create User (with validation):**
   ```bash
   curl -X POST http://localhost:8080/api/users \
     -H "Content-Type: application/json" \
     -d '{"name": "John Doe", "email": "john@example.com"}'
   ```

   **Health Check:**
   ```bash
   curl http://localhost:8080/api/health
   ```

   **Metrics:**
   ```bash
   curl http://localhost:8080/actuator/metrics
   ```

## 🧪 Testing Circuit Breaker

The `/api/users/{id}` endpoint has a 30% failure rate to demonstrate circuit breaker behavior:

```bash
# Make multiple requests to trigger circuit breaker
for i in {1..10}; do
  curl http://localhost:8080/api/users/1 && echo
  sleep 1
done
```

Watch the circuit breaker state change in the health endpoint:
```bash
curl http://localhost:8080/api/health | jq .circuitBreaker
```

## 📊 Monitoring

- **Health**: http://localhost:8080/actuator/health
- **Metrics**: http://localhost:8080/actuator/metrics  
- **Prometheus**: http://localhost:8080/actuator/prometheus

## 🔄 Migration Example

**Before (Traditional Exception Handling):**
```java
@GetMapping("/users/{id}")
public UserDto getUser(@PathVariable Long id) throws UserNotFoundException {
    if (id <= 0) {
        throw new IllegalArgumentException("Invalid user ID");
    }
    
    UserDto user = userService.findById(id);
    if (user == null) {
        throw new UserNotFoundException("User not found: " + id);
    }
    
    return user;
}
```

**After (JMonadic):**
```java
@GetMapping("/users/{id}")
public Result<UserDto, String> getUser(@PathVariable Long id) {
    return Result.of(() -> validateId(id))
        .flatMap(validId -> userService.findById(validId))
        .flatMap(this::enrichUserData)
        .peekSuccess(user -> logger.logSuccess("getUser", user, context))
        .peekError(error -> logger.logFailure("getUser", error, context));
}
```

## 📈 Benefits Demonstrated

✅ **No more exception handling boilerplate**  
✅ **Composable error handling pipelines**  
✅ **Built-in resilience patterns**  
✅ **Comprehensive observability**  
✅ **Type-safe error handling**  
✅ **Production-ready monitoring**

This example shows how JMonadic transforms your error handling from imperative exceptions to functional, observable, and resilient monadic patterns! 🎯