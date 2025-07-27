# Exception Handling Patterns 📚

Modern Java exception handling has evolved beyond traditional try-catch blocks. This guide covers cutting-edge patterns that enable cleaner, more composable, and more reliable error handling.

## 🎯 Core Patterns

### 1. Result Pattern

The `Result<T, E>` type represents the outcome of an operation that can either succeed with a value of type `T` or fail with an error of type `E`.

```java
// Traditional approach
public String parseAndDouble(String input) throws NumberFormatException {
    int number = Integer.parseInt(input);
    return String.valueOf(number * 2);
}

// Modern Result approach
public Result<String, Exception> parseAndDouble(String input) {
    return Result.of(() -> input)
        .map(Integer::parseInt)
        .map(i -> i * 2)
        .map(String::valueOf);
}
```

**Benefits:**
- ✅ No checked exceptions to clutter method signatures
- ✅ Explicit error handling at the call site
- ✅ Composable operations with map/flatMap
- ✅ Type-safe error propagation

### 2. Either Pattern

`Either<L, R>` represents a value that can be one of two types. By convention, `Left` is used for errors and `Right` for success values.

```java
// Validation with Either
public Either<String, User> validateUser(String name, int age) {
    if (name == null || name.trim().isEmpty()) {
        return Either.left("Name cannot be empty");
    }
    if (age < 0 || age > 150) {
        return Either.left("Invalid age: " + age);
    }
    return Either.right(new User(name, age));
}
```

**Use Cases:**
- 🎯 Input validation
- 🎯 Configuration parsing
- 🎯 Data transformation pipelines
- 🎯 API response handling

### 3. Try Pattern

`Try<T>` wraps computations that may throw exceptions, providing a functional approach to exception handling.

```java
// Safe resource handling
public Try<String> readFile(String filename) {
    return Try.of(() -> Files.readString(Paths.get(filename)))
        .recover(IOException.class, ex -> "Default content")
        .map(String::trim);
}
```

## 🔄 Composition Patterns

### Chaining Operations

```java
Result<User, String> result = validateEmail(email)
    .flatMap(this::checkEmailExists)
    .flatMap(this::createUser)
    .map(this::enrichUserData)
    .peekError(error -> logValidationError(error));
```

### Error Recovery

```java
Result<String, Exception> resilientOperation = primaryService()
    .recover(error -> fallbackService())
    .recoverWith(error -> "Default value");
```

### Pipeline Transformation

```java
Result<ProcessedData, Exception> pipeline = loadRawData()
    .filter(data -> data.isValid(), () -> new ValidationException("Invalid data"))
    .map(this::cleanData)
    .flatMap(this::enrichData)
    .map(this::processData);
```

## 🎨 Advanced Patterns

### 1. Monad Pattern Implementation

All our error handling types (`Result`, `Either`, `Try`) implement monadic patterns:

- **Functor**: `map` operation for transforming values
- **Applicative**: Combining multiple operations
- **Monad**: `flatMap` for chaining dependent operations

### 2. Resource Management

```java
public Result<String, Exception> processFile(String filename) {
    return Try.withResources(
        () -> Files.newBufferedReader(Paths.get(filename))
    ).flatMap(reader -> 
        Result.of(() -> reader.lines()
            .collect(Collectors.joining("\n")))
    ).toResult();
}
```

### 3. Parallel Error Handling

```java
CompletableFuture<Result<CombinedData, Exception>> parallelProcessing = 
    CompletableFuture.allOf(
        processDataAsync(data1).thenApply(Result::of),
        processDataAsync(data2).thenApply(Result::of),
        processDataAsync(data3).thenApply(Result::of)
    ).thenApply(v -> combineResults(results));
```

## 🔧 Practical Guidelines

### When to Use Each Pattern

| Pattern | Best For | Example Use Cases |
|---------|----------|-------------------|
| `Result<T, E>` | General error handling | API responses, business logic |
| `Either<L, R>` | Validation, data parsing | Form validation, configuration |
| `Try<T>` | Exception-heavy operations | File I/O, network calls |

### Performance Considerations

1. **Object Allocation**: These patterns create wrapper objects
2. **Stack Traces**: Consider disabling for performance-critical paths
3. **Memory Usage**: Be mindful in high-throughput scenarios

```java
// Performance-optimized error handling
private static final Result<String, Exception> CACHED_ERROR = 
    Result.failure(new CachedException("Common error"));

public Result<String, Exception> fastFailOperation() {
    if (shouldFail()) {
        return CACHED_ERROR; // Reuse exception instance
    }
    return Result.success(computeValue());
}
```

### Testing Strategies

```java
@Test
void shouldHandleValidationErrors() {
    Result<User, String> result = userService.createUser("", -1);
    
    assertThat(result.isFailure()).isTrue();
    assertThat(result.getError()).contains("validation");
}

@Test
void shouldChainOperationsSuccessfully() {
    Result<String, Exception> result = dataProcessor
        .loadData()
        .map(this::transform)
        .filter(data -> data.isValid(), 
               () -> new ValidationException("Invalid"));
    
    assertThat(result.isSuccess()).isTrue();
}
```

## 💡 Pro Tips

### 1. Error Context Preservation

```java
public Result<Data, DomainError> processData(String input) {
    return validateInput(input)
        .mapError(validationError -> DomainError.validation(validationError, input))
        .flatMap(this::transform)
        .mapError(transformError -> DomainError.transformation(transformError, input));
}
```

### 2. Early Returns with Pattern Matching

```java
public String handleResult(Result<String, Exception> result) {
    return switch (result) {
        case Result.Success(var value) -> "Success: " + value;
        case Result.Failure(var error) -> "Error: " + error.getMessage();
    };
}
```

### 3. Builder Pattern for Complex Operations

```java
public class DataProcessor {
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private final List<Function<Data, Result<Data, Exception>>> steps = new ArrayList<>();
        
        public Builder validate(Predicate<Data> predicate, String error) {
            steps.add(data -> predicate.test(data) 
                ? Result.success(data) 
                : Result.failure(new ValidationException(error)));
            return this;
        }
        
        public Builder transform(Function<Data, Data> transformer) {
            steps.add(data -> Result.of(() -> transformer.apply(data)));
            return this;
        }
        
        public Result<Data, Exception> process(Data input) {
            return steps.stream()
                .reduce(Result.success(input),
                       (result, step) -> result.flatMap(step),
                       (r1, r2) -> r1); // Never used in sequential stream
        }
    }
}
```

## 🚀 Migration Strategy

### Gradual Adoption

1. **Start with new code**: Use patterns in new features
2. **Wrap existing APIs**: Create adapters for legacy code
3. **Refactor incrementally**: Convert methods one at a time

### Legacy Integration

```java
// Wrapping legacy code
public Result<String, Exception> legacyOperation(String input) {
    return Result.of(() -> legacyService.process(input));
}

// Converting from exceptions
public Either<ErrorCode, Data> modernOperation(String input) {
    try {
        Data result = legacyService.process(input);
        return Either.right(result);
    } catch (ValidationException e) {
        return Either.left(ErrorCode.VALIDATION_ERROR);
    } catch (ProcessingException e) {
        return Either.left(ErrorCode.PROCESSING_ERROR);
    }
}
```
