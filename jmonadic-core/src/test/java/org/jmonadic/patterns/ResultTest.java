package org.jmonadic.patterns;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Result Pattern Tests")
class ResultTest {
    
    @Nested
    @DisplayName("Construction")
    class Construction {
        
        @Test
        @DisplayName("Should create Success with value")
        void shouldCreateSuccessWithValue() {
            String value = "test";
            Result<String, String> result = Result.success(value);
            
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.isFailure()).isFalse();
            assertThat(result.getValue()).isEqualTo(value);
        }
        
        @Test
        @DisplayName("Should create Failure with error")
        void shouldCreateFailureWithError() {
            String error = "error message";
            Result<String, String> result = Result.failure(error);
            
            assertThat(result.isFailure()).isTrue();
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getError()).isEqualTo(error);
        }
        
        @Test
        @DisplayName("Should allow null value in Success")
        void shouldAllowNullValueInSuccess() {
            Result<String, String> result = Result.success(null);
            
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getValue()).isNull();
        }
        
        @Test
        @DisplayName("Should reject null error in Failure")
        void shouldRejectNullErrorInFailure() {
            assertThatThrownBy(() -> Result.failure(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("Failure error cannot be null");
        }
        
        @Test
        @DisplayName("Should create Success from successful supplier")
        void shouldCreateSuccessFromSuccessfulSupplier() {
            Result<String, Exception> result = Result.of(() -> "success");
            
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getValue()).isEqualTo("success");
        }
        
        @Test
        @DisplayName("Should create Failure from failing supplier")
        void shouldCreateFailureFromFailingSupplier() {
            RuntimeException exception = new RuntimeException("failure");
            Result<String, Exception> result = Result.of(() -> {
                throw exception;
            });
            
            assertThat(result.isFailure()).isTrue();
            assertThat(result.getError()).isEqualTo(exception);
        }
        
        @Test
        @DisplayName("Should create Success from successful runnable")
        void shouldCreateSuccessFromSuccessfulRunnable() {
            AtomicBoolean executed = new AtomicBoolean(false);
            Result<Void, Exception> result = Result.ofVoid(() -> executed.set(true));
            
            assertThat(result.isSuccess()).isTrue();
            assertThat(executed.get()).isTrue();
            assertThat(result.getValue()).isNull();
        }
        
        @Test
        @DisplayName("Should create Failure from failing runnable")
        void shouldCreateFailureFromFailingRunnable() {
            RuntimeException exception = new RuntimeException("runnable failed");
            Result<Void, Exception> result = Result.ofVoid(() -> {
                throw exception;
            });
            
            assertThat(result.isFailure()).isTrue();
            assertThat(result.getError()).isEqualTo(exception);
        }
    }
    
    @Nested
    @DisplayName("Access Methods")
    class AccessMethods {
        
        @Test
        @DisplayName("Should throw when getting error from Success")
        void shouldThrowWhenGettingErrorFromSuccess() {
            Result<String, String> result = Result.success("test");
            
            assertThatThrownBy(result::getError)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Cannot get error from Success");
        }
        
        @Test
        @DisplayName("Should throw when getting value from Failure")
        void shouldThrowWhenGettingValueFromFailure() {
            Result<String, String> result = Result.failure("error");
            
            assertThatThrownBy(result::getValue)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Cannot get value from Failure");
        }
        
        @Test
        @DisplayName("Should return value with getOrElse for Success")
        void shouldReturnValueWithGetOrElseForSuccess() {
            Result<String, String> result = Result.success("test");
            String value = result.getOrElse("default");
            
            assertThat(value).isEqualTo("test");
        }
        
        @Test
        @DisplayName("Should return default with getOrElse for Failure")
        void shouldReturnDefaultWithGetOrElseForFailure() {
            Result<String, String> result = Result.failure("error");
            String value = result.getOrElse("default");
            
            assertThat(value).isEqualTo("default");
        }
        
        @Test
        @DisplayName("Should return value with getOrElse function for Success")
        void shouldReturnValueWithGetOrElseFunctionForSuccess() {
            Result<String, String> result = Result.success("test");
            String value = result.getOrElse(error -> "Error: " + error);
            
            assertThat(value).isEqualTo("test");
        }
        
        @Test
        @DisplayName("Should apply function with getOrElse for Failure")
        void shouldApplyFunctionWithGetOrElseForFailure() {
            Result<String, String> result = Result.failure("error");
            String value = result.getOrElse(error -> "Error: " + error);
            
            assertThat(value).isEqualTo("Error: error");
        }
    }
    
    @Nested
    @DisplayName("Transformation")
    class Transformation {
        
        @Test
        @DisplayName("Should map Success value")
        void shouldMapSuccessValue() {
            Result<String, String> result = Result.success("42");
            Result<Integer, String> mapped = result.map(Integer::parseInt);
            
            assertThat(mapped.isSuccess()).isTrue();
            assertThat(mapped.getValue()).isEqualTo(42);
        }
        
        @Test
        @DisplayName("Should not map Failure value")
        void shouldNotMapFailureValue() {
            Result<String, String> result = Result.failure("error");
            Result<Integer, String> mapped = result.map(Integer::parseInt);
            
            assertThat(mapped.isFailure()).isTrue();
            assertThat(mapped.getError()).isEqualTo("error");
        }
        
        @Test
        @DisplayName("Should map error value with mapError")
        void shouldMapErrorValueWithMapError() {
            Result<String, String> result = Result.failure("error");
            Result<String, Integer> mapped = result.mapError(String::length);
            
            assertThat(mapped.isFailure()).isTrue();
            assertThat(mapped.getError()).isEqualTo(5);
        }
        
        @Test
        @DisplayName("Should not map Success value with mapError")
        void shouldNotMapSuccessValueWithMapError() {
            Result<String, String> result = Result.success("test");
            Result<String, Integer> mapped = result.mapError(String::length);
            
            assertThat(mapped.isSuccess()).isTrue();
            assertThat(mapped.getValue()).isEqualTo("test");
        }
        
        @Test
        @DisplayName("Should flatMap Success value")
        void shouldFlatMapSuccessValue() {
            Result<String, String> result = Result.success("42");
            Result<Integer, String> flatMapped = result.flatMap(s -> Result.success(Integer.parseInt(s)));
            
            assertThat(flatMapped.isSuccess()).isTrue();
            assertThat(flatMapped.getValue()).isEqualTo(42);
        }
        
        @Test
        @DisplayName("Should flatMap Success to Failure")
        void shouldFlatMapSuccessToFailure() {
            Result<String, String> result = Result.success("test");
            Result<Integer, String> flatMapped = result.flatMap(s -> Result.failure("conversion error"));
            
            assertThat(flatMapped.isFailure()).isTrue();
            assertThat(flatMapped.getError()).isEqualTo("conversion error");
        }
        
        @Test
        @DisplayName("Should not flatMap Failure value")
        void shouldNotFlatMapFailureValue() {
            Result<String, String> result = Result.failure("error");
            Result<Integer, String> flatMapped = result.flatMap(s -> Result.success(Integer.parseInt(s)));
            
            assertThat(flatMapped.isFailure()).isTrue();
            assertThat(flatMapped.getError()).isEqualTo("error");
        }
        
        @Test
        @DisplayName("Should filter Success with passing predicate")
        void shouldFilterSuccessWithPassingPredicate() {
            Result<String, String> result = Result.success("test");
            Result<String, String> filtered = result.filter(s -> s.length() > 3, () -> "too short");
            
            assertThat(filtered.isSuccess()).isTrue();
            assertThat(filtered.getValue()).isEqualTo("test");
        }
        
        @Test
        @DisplayName("Should filter Success with failing predicate")
        void shouldFilterSuccessWithFailingPredicate() {
            Result<String, String> result = Result.success("hi");
            Result<String, String> filtered = result.filter(s -> s.length() > 3, () -> "too short");
            
            assertThat(filtered.isFailure()).isTrue();
            assertThat(filtered.getError()).isEqualTo("too short");
        }
        
        @Test
        @DisplayName("Should not filter Failure value")
        void shouldNotFilterFailureValue() {
            Result<String, String> result = Result.failure("error");
            Result<String, String> filtered = result.filter(s -> true, () -> "filter error");
            
            assertThat(filtered.isFailure()).isTrue();
            assertThat(filtered.getError()).isEqualTo("error");
        }
    }
    
    @Nested
    @DisplayName("Side Effects")
    class SideEffects {
        
        @Test
        @DisplayName("Should execute success consumer for Success")
        void shouldExecuteSuccessConsumerForSuccess() {
            Result<String, String> result = Result.success("test");
            AtomicBoolean successCalled = new AtomicBoolean(false);
            AtomicBoolean errorCalled = new AtomicBoolean(false);
            
            Result<String, String> peekResult = result.peek(
                value -> successCalled.set(true),
                error -> errorCalled.set(true)
            );
            
            assertThat(peekResult).isSameAs(result);
            assertThat(successCalled.get()).isTrue();
            assertThat(errorCalled.get()).isFalse();
        }
        
        @Test
        @DisplayName("Should execute error consumer for Failure")
        void shouldExecuteErrorConsumerForFailure() {
            Result<String, String> result = Result.failure("error");
            AtomicBoolean successCalled = new AtomicBoolean(false);
            AtomicBoolean errorCalled = new AtomicBoolean(false);
            
            Result<String, String> peekResult = result.peek(
                value -> successCalled.set(true),
                error -> errorCalled.set(true)
            );
            
            assertThat(peekResult).isSameAs(result);
            assertThat(successCalled.get()).isFalse();
            assertThat(errorCalled.get()).isTrue();
        }
        
        @Test
        @DisplayName("Should execute peekSuccess for Success")
        void shouldExecutePeekSuccessForSuccess() {
            Result<String, String> result = Result.success("test");
            AtomicInteger counter = new AtomicInteger(0);
            
            Result<String, String> peekResult = result.peekSuccess(value -> counter.incrementAndGet());
            
            assertThat(peekResult).isSameAs(result);
            assertThat(counter.get()).isEqualTo(1);
        }
        
        @Test
        @DisplayName("Should not execute peekSuccess for Failure")
        void shouldNotExecutePeekSuccessForFailure() {
            Result<String, String> result = Result.failure("error");
            AtomicInteger counter = new AtomicInteger(0);
            
            Result<String, String> peekResult = result.peekSuccess(value -> counter.incrementAndGet());
            
            assertThat(peekResult).isSameAs(result);
            assertThat(counter.get()).isEqualTo(0);
        }
        
        @Test
        @DisplayName("Should execute peekError for Failure")
        void shouldExecutePeekErrorForFailure() {
            Result<String, String> result = Result.failure("error");
            AtomicInteger counter = new AtomicInteger(0);
            
            Result<String, String> peekResult = result.peekError(error -> counter.incrementAndGet());
            
            assertThat(peekResult).isSameAs(result);
            assertThat(counter.get()).isEqualTo(1);
        }
        
        @Test
        @DisplayName("Should not execute peekError for Success")
        void shouldNotExecutePeekErrorForSuccess() {
            Result<String, String> result = Result.success("test");
            AtomicInteger counter = new AtomicInteger(0);
            
            Result<String, String> peekResult = result.peekError(error -> counter.incrementAndGet());
            
            assertThat(peekResult).isSameAs(result);
            assertThat(counter.get()).isEqualTo(0);
        }
    }
    
    @Nested
    @DisplayName("Folding")
    class Folding {
        
        @Test
        @DisplayName("Should fold Success to success function result")
        void shouldFoldSuccessToSuccessFunctionResult() {
            Result<String, String> result = Result.success("test");
            
            String folded = result.fold(
                value -> "Success: " + value,
                error -> "Error: " + error
            );
            
            assertThat(folded).isEqualTo("Success: test");
        }
        
        @Test
        @DisplayName("Should fold Failure to error function result")
        void shouldFoldFailureToErrorFunctionResult() {
            Result<String, String> result = Result.failure("error");
            
            String folded = result.fold(
                value -> "Success: " + value,
                error -> "Error: " + error
            );
            
            assertThat(folded).isEqualTo("Error: error");
        }
    }
    
    @Nested
    @DisplayName("Recovery")
    class Recovery {
        
        @Test
        @DisplayName("Should not recover Success")
        void shouldNotRecoverSuccess() {
            Result<String, String> result = Result.success("test");
            Result<String, String> recovered = result.recover(error -> Result.success("recovered"));
            
            assertThat(recovered.isSuccess()).isTrue();
            assertThat(recovered.getValue()).isEqualTo("test");
            assertThat(recovered).isSameAs(result);
        }
        
        @Test
        @DisplayName("Should recover Failure to Success")
        void shouldRecoverFailureToSuccess() {
            Result<String, String> result = Result.failure("error");
            Result<String, String> recovered = result.recover(error -> Result.success("recovered from: " + error));
            
            assertThat(recovered.isSuccess()).isTrue();
            assertThat(recovered.getValue()).isEqualTo("recovered from: error");
        }
        
        @Test
        @DisplayName("Should recover Failure to Failure")
        void shouldRecoverFailureToFailure() {
            Result<String, String> result = Result.failure("original error");
            Result<String, String> recovered = result.recover(error -> Result.failure("recovery error"));
            
            assertThat(recovered.isFailure()).isTrue();
            assertThat(recovered.getError()).isEqualTo("recovery error");
        }
        
        @Test
        @DisplayName("Should not recoverWith Success")
        void shouldNotRecoverWithSuccess() {
            Result<String, String> result = Result.success("test");
            Result<String, String> recovered = result.recoverWith(error -> "recovered");
            
            assertThat(recovered.isSuccess()).isTrue();
            assertThat(recovered.getValue()).isEqualTo("test");
            assertThat(recovered).isSameAs(result);
        }
        
        @Test
        @DisplayName("Should recoverWith Failure")
        void shouldRecoverWithFailure() {
            Result<String, String> result = Result.failure("error");
            Result<String, String> recovered = result.recoverWith(error -> "recovered from: " + error);
            
            assertThat(recovered.isSuccess()).isTrue();
            assertThat(recovered.getValue()).isEqualTo("recovered from: error");
        }
    }
    
    @Nested
    @DisplayName("Chaining and Composition")
    class ChainingAndComposition {
        
        @Test
        @DisplayName("Should chain multiple operations successfully")
        void shouldChainMultipleOperationsSuccessfully() {
            Result<String, String> result = Result.<String, String>success("42")
                .map(Integer::parseInt)
                .filter(i -> i > 40, () -> "too small")
                .map(i -> i * 2)
                .map(i -> "Result: " + i);
            
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getValue()).isEqualTo("Result: 84");
        }
        
        @Test
        @DisplayName("Should short-circuit on first error")
        void shouldShortCircuitOnFirstError() {
            Result<String, String> result = Result.<String, String>success("42")
                .map(Integer::parseInt)
                .filter(i -> i > 50, () -> "too small")  // This will fail
                .map(i -> i * 2)  // This should not execute
                .map(i -> "Result: " + i);  // This should not execute
            
            assertThat(result.isFailure()).isTrue();
            assertThat(result.getError()).isEqualTo("too small");
        }
        
        @Test
        @DisplayName("Should compose with flatMap for dependent operations")
        void shouldComposeWithFlatMapForDependentOperations() {
            Result<Integer, String> result = Result.<String, String>success("42")
                .flatMap(s -> {
                    try {
                        return Result.<Integer, String>success(Integer.parseInt(s));
                    } catch (NumberFormatException e) {
                        return Result.<Integer, String>failure("Invalid number: " + s);
                    }
                })
                .flatMap(i -> i > 0 ? Result.<Integer, String>success(i * 2) : Result.<Integer, String>failure("Non-positive number"));
            
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getValue()).isEqualTo(84);
        }
        
        @Test
        @DisplayName("Should handle validation pipeline")
        void shouldHandleValidationPipeline() {
            record User(String name, int age, String email) {}
            
            Result<User, String> result = Result.<User, String>success(new User("John", 25, "john@example.com"))
                .filter(u -> u.name() != null && !u.name().trim().isEmpty(), () -> "Name is required")
                .filter(u -> u.age() >= 0 && u.age() <= 150, () -> "Invalid age")
                .filter(u -> u.email() != null && u.email().contains("@"), () -> "Invalid email");
            
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getValue().name()).isEqualTo("John");
        }
        
        @Test
        @DisplayName("Should fail validation pipeline on first invalid field")
        void shouldFailValidationPipelineOnFirstInvalidField() {
            record User(String name, int age, String email) {}
            
            Result<User, String> result = Result.<User, String>success(new User("", 25, "john@example.com"))
                .filter(u -> u.name() != null && !u.name().trim().isEmpty(), () -> "Name is required")
                .filter(u -> u.age() >= 0 && u.age() <= 150, () -> "Invalid age")
                .filter(u -> u.email() != null && u.email().contains("@"), () -> "Invalid email");
            
            assertThat(result.isFailure()).isTrue();
            assertThat(result.getError()).isEqualTo("Name is required");
        }
        
        @Test
        @DisplayName("Should recover from errors in chain")
        void shouldRecoverFromErrorsInChain() {
            Result<String, String> result = Result.<String, String>success("not a number")
                .flatMap(s -> {
                    try {
                        return Result.<Integer, String>success(Integer.parseInt(s));
                    } catch (NumberFormatException e) {
                        return Result.<Integer, String>failure("Parse error");
                    }
                })
                .recover(error -> Result.<Integer, String>success(0))  // Recover with default value
                .map(i -> "Result: " + i);
            
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getValue()).isEqualTo("Result: 0");
        }
    }
    
    @Nested
    @DisplayName("Equality and String Representation")
    class EqualityAndStringRepresentation {
        
        @Test
        @DisplayName("Should have equal Success instances with same value")
        void shouldHaveEqualSuccessInstancesWithSameValue() {
            Result<String, String> result1 = Result.success("test");
            Result<String, String> result2 = Result.success("test");
            
            assertThat(result1).isEqualTo(result2);
            assertThat(result1.hashCode()).isEqualTo(result2.hashCode());
        }
        
        @Test
        @DisplayName("Should have unequal Success instances with different values")
        void shouldHaveUnequalSuccessInstancesWithDifferentValues() {
            Result<String, String> result1 = Result.success("test1");
            Result<String, String> result2 = Result.success("test2");
            
            assertThat(result1).isNotEqualTo(result2);
        }
        
        @Test
        @DisplayName("Should have equal Failure instances with same error")
        void shouldHaveEqualFailureInstancesWithSameError() {
            Result<String, String> result1 = Result.failure("error");
            Result<String, String> result2 = Result.failure("error");
            
            assertThat(result1).isEqualTo(result2);
            assertThat(result1.hashCode()).isEqualTo(result2.hashCode());
        }
        
        @Test
        @DisplayName("Should have unequal Failure instances with different errors")
        void shouldHaveUnequalFailureInstancesWithDifferentErrors() {
            Result<String, String> result1 = Result.failure("error1");
            Result<String, String> result2 = Result.failure("error2");
            
            assertThat(result1).isNotEqualTo(result2);
        }
        
        @Test
        @DisplayName("Should have different Success and Failure instances")
        void shouldHaveDifferentSuccessAndFailureInstances() {
            Result<String, String> success = Result.success("test");
            Result<String, String> failure = Result.failure("error");
            
            assertThat(success).isNotEqualTo(failure);
        }
        
        @Test
        @DisplayName("Should have meaningful string representation for Success")
        void shouldHaveMeaningfulStringRepresentationForSuccess() {
            Result<String, String> result = Result.success("test");
            
            assertThat(result.toString()).contains("Success");
            assertThat(result.toString()).contains("test");
        }
        
        @Test
        @DisplayName("Should have meaningful string representation for Failure")
        void shouldHaveMeaningfulStringRepresentationForFailure() {
            Result<String, String> result = Result.failure("error");
            
            assertThat(result.toString()).contains("Failure");
            assertThat(result.toString()).contains("error");
        }
    }
    
    @Nested
    @DisplayName("Real World Use Cases")
    class RealWorldUseCases {
        
        @Test
        @DisplayName("Should handle API response parsing")
        void shouldHandleApiResponseParsing() {
            // Simulate API response parsing
            Result<Integer, String> result = Result.<String, String>success("123")  // Simulate HTTP response
                .flatMap(response -> {
                    if (response.trim().isEmpty()) {
                        return Result.<String, String>failure("Empty response");
                    }
                    return Result.<String, String>success(response.trim());
                })
                .flatMap(data -> {
                    try {
                        return Result.<Integer, String>success(Integer.parseInt(data));
                    } catch (NumberFormatException e) {
                        return Result.<Integer, String>failure("Invalid number format: " + data);
                    }
                })
                .filter(value -> value > 0, () -> "Value must be positive");
            
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getValue()).isEqualTo(123);
        }
        
        @Test
        @DisplayName("Should handle business logic workflow")
        void shouldHandleBusinessLogicWorkflow() {
            record Order(String id, double amount, String customerId) {}
            record Customer(String id, boolean isActive, double creditLimit) {}
            
            // Simulate business workflow
            Result<String, String> result = Result.<Order, String>success(new Order("ORD-123", 100.0, "CUST-456"))
                .filter(order -> order.amount() > 0, () -> "Invalid order amount")
                .flatMap(order -> {
                    // Simulate customer lookup
                    Customer customer = new Customer("CUST-456", true, 500.0);
                    return Result.<Customer, String>success(customer)
                        .filter(c -> c.isActive(), () -> "Customer is not active")
                        .filter(c -> c.creditLimit() >= order.amount(), () -> "Insufficient credit limit")
                        .map(c -> order);
                })
                .map(order -> "Order " + order.id() + " processed successfully");
            
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getValue()).isEqualTo("Order ORD-123 processed successfully");
        }
        
        @Test
        @DisplayName("Should handle configuration loading with fallbacks")
        void shouldHandleConfigurationLoadingWithFallbacks() {
            // Simulate configuration loading with multiple fallbacks
            Result<String, String> step1 = Result.<String, String>failure("Primary config file not found");
            Result<String, String> step2 = step1.recover(error -> Result.<String, String>failure("Fallback config not found"));
            Result<String, String> config = step2.recover(error -> Result.<String, String>success("default-value"))
                .filter(value -> !value.isEmpty(), () -> "Config value cannot be empty");
            
            assertThat(config.isSuccess()).isTrue();
            assertThat(config.getValue()).isEqualTo("default-value");
        }
        
        @Test
        @DisplayName("Should handle data transformation pipeline")
        void shouldHandleDataTransformationPipeline() {
            // Simulate ETL pipeline
            Result<String, String> pipeline = Result.<String, String>success("  raw,data,123  ")
                .map(String::trim)
                .filter(data -> !data.isEmpty(), () -> "Empty input data")
                .map(data -> data.split(","))
                .filter(parts -> parts.length == 3, () -> "Invalid data format")
                .flatMap(parts -> {
                    try {
                        int number = Integer.parseInt(parts[2].trim());
                        return Result.<String, String>success(parts[0].trim() + "-" + parts[1].trim() + "-" + number);
                    } catch (NumberFormatException e) {
                        return Result.<String, String>failure("Invalid number in data: " + parts[2]);
                    }
                })
                .peekSuccess(data -> System.out.println("Processed: " + data))
                .peekError(error -> System.err.println("Error: " + error));
            
            assertThat(pipeline.isSuccess()).isTrue();
            assertThat(pipeline.getValue()).isEqualTo("raw-data-123");
        }
    }
    
    @Nested
    @DisplayName("Exception Handling in Transformations")
    class ExceptionHandlingInTransformations {
        
        @Test
        @DisplayName("Should handle exceptions in map function")
        void shouldHandleExceptionsInMapFunction() {
            Result<String, String> result = Result.success("42");
            
            // This test verifies that exceptions in map are not automatically caught
            // The Result pattern using `map` doesn't automatically wrap exceptions
            assertThatThrownBy(() -> result.map(s -> {
                throw new RuntimeException("map failed");
            })).isInstanceOf(RuntimeException.class)
              .hasMessage("map failed");
        }
        
        @Test
        @DisplayName("Should handle exceptions in flatMap function")
        void shouldHandleExceptionsInFlatMapFunction() {
            Result<String, String> result = Result.success("42");
            
            assertThatThrownBy(() -> result.flatMap(s -> {
                throw new RuntimeException("flatMap failed");
            })).isInstanceOf(RuntimeException.class)
              .hasMessage("flatMap failed");
        }
        
        @Test
        @DisplayName("Should safely handle exceptions with Result.of in chain")
        void shouldSafelyHandleExceptionsWithResultOfInChain() {
            Result<Integer, Exception> result = Result.<String, Exception>success("42")
                .flatMap(s -> Result.<Integer>of(() -> Integer.parseInt(s)))
                .flatMap(i -> Result.<Integer>of(() -> {
                    if (i == 42) {
                        throw new IllegalArgumentException("42 is not allowed");
                    }
                    return i * 2;
                }));
            
            assertThat(result.isFailure()).isTrue();
            assertThat(result.getError()).isInstanceOf(IllegalArgumentException.class);
            assertThat(result.getError().getMessage()).isEqualTo("42 is not allowed");
        }
    }
}