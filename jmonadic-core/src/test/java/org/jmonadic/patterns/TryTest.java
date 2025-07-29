package org.jmonadic.patterns;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Try Pattern Tests")
class TryTest {
    
    @Nested
    @DisplayName("Construction")
    class Construction {
        
        @Test
        @DisplayName("Should create Success from successful supplier")
        void shouldCreateSuccessFromSuccessfulSupplier() {
            Try<String> tryResult = Try.of(() -> "success");
            
            assertThat(tryResult.isSuccess()).isTrue();
            assertThat(tryResult.isFailure()).isFalse();
            assertThat(tryResult.getOrElse("")).isEqualTo("success");
        }
        
        @Test
        @DisplayName("Should create Failure from failing supplier")
        void shouldCreateFailureFromFailingSupplier() {
            RuntimeException exception = new RuntimeException("failure");
            Try<String> tryResult = Try.of(() -> {
                throw exception;
            });
            
            assertThat(tryResult.isFailure()).isTrue();
            assertThat(tryResult.isSuccess()).isFalse();
            assertThatThrownBy(tryResult::get).isEqualTo(exception);
        }
        
        @Test
        @DisplayName("Should create Success from successful runnable")
        void shouldCreateSuccessFromSuccessfulRunnable() {
            AtomicBoolean executed = new AtomicBoolean(false);
            Try<Void> tryResult = Try.run(() -> executed.set(true));
            
            assertThat(tryResult.isSuccess()).isTrue();
            assertThat(executed.get()).isTrue();
            assertThat(tryResult.getOrElse((Void) null)).isNull();
        }
        
        @Test
        @DisplayName("Should create Failure from failing runnable")
        void shouldCreateFailureFromFailingRunnable() {
            RuntimeException exception = new RuntimeException("runnable failed");
            Try<Void> tryResult = Try.run(() -> {
                throw exception;
            });
            
            assertThat(tryResult.isFailure()).isTrue();
            assertThatThrownBy(tryResult::get).isEqualTo(exception);
        }
        
        @Test
        @DisplayName("Should create Success explicitly")
        void shouldCreateSuccessExplicitly() {
            Try<String> tryResult = Try.success("test");
            
            assertThat(tryResult.isSuccess()).isTrue();
            assertThat(tryResult.getOrElse("")).isEqualTo("test");
        }
        
        @Test
        @DisplayName("Should create Failure explicitly")
        void shouldCreateFailureExplicitly() {
            IOException exception = new IOException("test error");
            Try<String> tryResult = Try.failure(exception);
            
            assertThat(tryResult.isFailure()).isTrue();
            assertThatThrownBy(tryResult::get).isEqualTo(exception);
        }
        
        @Test
        @DisplayName("Should handle null value in Success")
        void shouldHandleNullValueInSuccess() {
            Try<String> tryResult = Try.success(null);
            
            assertThat(tryResult.isSuccess()).isTrue();
            assertThat(tryResult.getOrElse((String) null)).isNull();
        }
    }
    
    @Nested
    @DisplayName("Value Access")
    class ValueAccess {
        
        @Test
        @DisplayName("Should return value with getOrElse for Success")
        void shouldReturnValueWithGetOrElseForSuccess() {
            Try<String> tryResult = Try.success("test");
            String result = tryResult.getOrElse("default");
            
            assertThat(result).isEqualTo("test");
        }
        
        @Test
        @DisplayName("Should return default with getOrElse for Failure")
        void shouldReturnDefaultWithGetOrElseForFailure() {
            Try<String> tryResult = Try.failure(new RuntimeException("error"));
            String result = tryResult.getOrElse("default");
            
            assertThat(result).isEqualTo("default");
        }
        
        @Test
        @DisplayName("Should return value with getOrElse function for Success")
        void shouldReturnValueWithGetOrElseFunctionForSuccess() {
            Try<String> tryResult = Try.success("test");
            String result = tryResult.getOrElse(ex -> "error: " + ex.getMessage());
            
            assertThat(result).isEqualTo("test");
        }
        
        @Test
        @DisplayName("Should apply function with getOrElse for Failure")
        void shouldApplyFunctionWithGetOrElseForFailure() {
            RuntimeException exception = new RuntimeException("original error");
            Try<String> tryResult = Try.failure(exception);
            String result = tryResult.getOrElse(ex -> "error: " + ex.getMessage());
            
            assertThat(result).isEqualTo("error: original error");
        }
        
        @Test
        @DisplayName("Should return empty Optional for exception in Success")
        void shouldReturnEmptyOptionalForExceptionInSuccess() {
            Try<String> tryResult = Try.success("test");
            Optional<Exception> exception = tryResult.getException();
            
            assertThat(exception).isEmpty();
        }
        
        @Test
        @DisplayName("Should return exception in Optional for Failure")
        void shouldReturnExceptionInOptionalForFailure() {
            RuntimeException exception = new RuntimeException("error");
            Try<String> tryResult = Try.failure(exception);
            Optional<Exception> result = tryResult.getException();
            
            assertThat(result).isPresent();
            assertThat(result.get()).isEqualTo(exception);
        }
    }
    
    @Nested
    @DisplayName("Transformation")
    class Transformation {
        
        @Test
        @DisplayName("Should map Success value")
        void shouldMapSuccessValue() {
            Try<String> tryResult = Try.success("42");
            Try<Integer> mapped = tryResult.map(Integer::parseInt);
            
            assertThat(mapped.isSuccess()).isTrue();
            assertThat(mapped.getOrElse(0)).isEqualTo(42);
        }
        
        @Test
        @DisplayName("Should not map Failure value")
        void shouldNotMapFailureValue() {
            RuntimeException exception = new RuntimeException("error");
            Try<String> tryResult = Try.failure(exception);
            Try<Integer> mapped = tryResult.map(Integer::parseInt);
            
            assertThat(mapped.isFailure()).isTrue();
            assertThat(mapped.getException().get()).isEqualTo(exception);
        }
        
        @Test
        @DisplayName("Should handle exception in map function")
        void shouldHandleExceptionInMapFunction() {
            Try<String> tryResult = Try.success("not a number");
            Try<Integer> mapped = tryResult.map(Integer::parseInt);
            
            assertThat(mapped.isFailure()).isTrue();
            assertThat(mapped.getException().get()).isInstanceOf(NumberFormatException.class);
        }
        
        @Test
        @DisplayName("Should flatMap Success value")
        void shouldFlatMapSuccessValue() {
            Try<String> tryResult = Try.success("42");
            Try<Integer> flatMapped = tryResult.flatMap(s -> Try.of(() -> Integer.parseInt(s)));
            
            assertThat(flatMapped.isSuccess()).isTrue();
            assertThat(flatMapped.getOrElse(0)).isEqualTo(42);
        }
        
        @Test
        @DisplayName("Should flatMap Success to Failure")
        void shouldFlatMapSuccessToFailure() {
            Try<String> tryResult = Try.success("not a number");
            Try<Integer> flatMapped = tryResult.flatMap(s -> Try.of(() -> Integer.parseInt(s)));
            
            assertThat(flatMapped.isFailure()).isTrue();
            assertThat(flatMapped.getException().get()).isInstanceOf(NumberFormatException.class);
        }
        
        @Test
        @DisplayName("Should not flatMap Failure value")
        void shouldNotFlatMapFailureValue() {
            RuntimeException exception = new RuntimeException("error");
            Try<String> tryResult = Try.failure(exception);
            Try<Integer> flatMapped = tryResult.flatMap(s -> Try.of(() -> Integer.parseInt(s)));
            
            assertThat(flatMapped.isFailure()).isTrue();
            assertThat(flatMapped.getException().get()).isEqualTo(exception);
        }
        
        @Test
        @DisplayName("Should handle exception in flatMap function")
        void shouldHandleExceptionInFlatMapFunction() {
            Try<String> tryResult = Try.success("test");
            Try<Integer> flatMapped = tryResult.flatMap(s -> {
                throw new RuntimeException("flatMap failed");
            });
            
            assertThat(flatMapped.isFailure()).isTrue();
            assertThat(flatMapped.getException().get()).hasMessage("flatMap failed");
        }
    }
    
    @Nested
    @DisplayName("Recovery")
    class Recovery {
        
        @Test
        @DisplayName("Should not recover Success")
        void shouldNotRecoverSuccess() {
            Try<String> tryResult = Try.success("test");
            Try<String> recovered = tryResult.recover(ex -> "recovered");
            
            assertThat(recovered.isSuccess()).isTrue();
            assertThat(recovered.getOrElse("")).isEqualTo("test");
            assertThat(recovered).isSameAs(tryResult);
        }
        
        @Test
        @DisplayName("Should recover Failure")
        void shouldRecoverFailure() {
            Try<String> tryResult = Try.failure(new RuntimeException("error"));
            Try<String> recovered = tryResult.recover(ex -> "recovered from: " + ex.getMessage());
            
            assertThat(recovered.isSuccess()).isTrue();
            assertThat(recovered.getOrElse("")).isEqualTo("recovered from: error");
        }
        
        @Test
        @DisplayName("Should handle exception in recovery function")
        void shouldHandleExceptionInRecoveryFunction() {
            Try<String> tryResult = Try.failure(new RuntimeException("original error"));
            Try<String> recovered = tryResult.recover(ex -> {
                throw new RuntimeException("recovery failed");
            });
            
            assertThat(recovered.isFailure()).isTrue();
            assertThat(recovered.getException().get()).hasMessage("recovery failed");
        }
        
        @Test
        @DisplayName("Should not recoverWith Success")
        void shouldNotRecoverWithSuccess() {
            Try<String> tryResult = Try.success("test");
            Try<String> recovered = tryResult.recoverWith(ex -> Try.success("recovered"));
            
            assertThat(recovered.isSuccess()).isTrue();
            assertThat(recovered.getOrElse("")).isEqualTo("test");
            assertThat(recovered).isSameAs(tryResult);
        }
        
        @Test
        @DisplayName("Should recoverWith Failure to Success")
        void shouldRecoverWithFailureToSuccess() {
            Try<String> tryResult = Try.failure(new RuntimeException("error"));
            Try<String> recovered = tryResult.recoverWith(ex -> Try.success("recovered from: " + ex.getMessage()));
            
            assertThat(recovered.isSuccess()).isTrue();
            assertThat(recovered.getOrElse("")).isEqualTo("recovered from: error");
        }
        
        @Test
        @DisplayName("Should recoverWith Failure to Failure")
        void shouldRecoverWithFailureToFailure() {
            Try<String> tryResult = Try.failure(new RuntimeException("original error"));
            Try<String> recovered = tryResult.recoverWith(ex -> Try.failure(new IOException("recovery error")));
            
            assertThat(recovered.isFailure()).isTrue();
            assertThat(recovered.getException().get()).isInstanceOf(IOException.class);
            assertThat(recovered.getException().get()).hasMessage("recovery error");
        }
        
        @Test
        @DisplayName("Should handle exception in recoverWith function")
        void shouldHandleExceptionInRecoverWithFunction() {
            Try<String> tryResult = Try.failure(new RuntimeException("original error"));
            Try<String> recovered = tryResult.recoverWith(ex -> {
                throw new RuntimeException("recoverWith failed");
            });
            
            assertThat(recovered.isFailure()).isTrue();
            assertThat(recovered.getException().get()).hasMessage("recoverWith failed");
        }
    }
    
    @Nested
    @DisplayName("Transformation and Folding")
    class TransformationAndFolding {
        
        @Test
        @DisplayName("Should transform Success")
        void shouldTransformSuccess() {
            Try<String> tryResult = Try.success("test");
            String result = tryResult.transform(
                value -> "Success: " + value,
                ex -> "Error: " + ex.getMessage()
            );
            
            assertThat(result).isEqualTo("Success: test");
        }
        
        @Test
        @DisplayName("Should transform Failure")
        void shouldTransformFailure() {
            Try<String> tryResult = Try.failure(new RuntimeException("error"));
            String result = tryResult.transform(
                value -> "Success: " + value,
                ex -> "Error: " + ex.getMessage()
            );
            
            assertThat(result).isEqualTo("Error: error");
        }
    }
    
    @Nested
    @DisplayName("Conversion")
    class Conversion {
        
        @Test
        @DisplayName("Should convert Success to Optional with value")
        void shouldConvertSuccessToOptionalWithValue() {
            Try<String> tryResult = Try.success("test");
            Optional<String> optional = tryResult.toOptional();
            
            assertThat(optional).isPresent();
            assertThat(optional.get()).isEqualTo("test");
        }
        
        @Test
        @DisplayName("Should convert Success with null to empty Optional")
        void shouldConvertSuccessWithNullToEmptyOptional() {
            Try<String> tryResult = Try.success(null);
            Optional<String> optional = tryResult.toOptional();
            
            assertThat(optional).isEmpty();
        }
        
        @Test
        @DisplayName("Should convert Failure to empty Optional")
        void shouldConvertFailureToEmptyOptional() {
            Try<String> tryResult = Try.failure(new RuntimeException("error"));
            Optional<String> optional = tryResult.toOptional();
            
            assertThat(optional).isEmpty();
        }
        
        @Test
        @DisplayName("Should convert Success to Either right")
        void shouldConvertSuccessToEitherRight() {
            Try<String> tryResult = Try.success("test");
            Either<Exception, String> either = tryResult.toEither();
            
            assertThat(either.isRight()).isTrue();
            assertThat(either.getRight()).isEqualTo("test");
        }
        
        @Test
        @DisplayName("Should convert Failure to Either left")
        void shouldConvertFailureToEitherLeft() {
            RuntimeException exception = new RuntimeException("error");
            Try<String> tryResult = Try.failure(exception);
            Either<Exception, String> either = tryResult.toEither();
            
            assertThat(either.isLeft()).isTrue();
            assertThat(either.getLeft()).isEqualTo(exception);
        }
        
        @Test
        @DisplayName("Should convert Success to Result success")
        void shouldConvertSuccessToResultSuccess() {
            Try<String> tryResult = Try.success("test");
            Result<String, Exception> result = tryResult.toResult();
            
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getValue()).isEqualTo("test");
        }
        
        @Test
        @DisplayName("Should convert Failure to Result failure")
        void shouldConvertFailureToResultFailure() {
            RuntimeException exception = new RuntimeException("error");
            Try<String> tryResult = Try.failure(exception);
            Result<String, Exception> result = tryResult.toResult();
            
            assertThat(result.isFailure()).isTrue();
            assertThat(result.getError()).isEqualTo(exception);
        }
    }
    
    @Nested
    @DisplayName("Chaining and Composition")
    class ChainingAndComposition {
        
        @Test
        @DisplayName("Should chain multiple operations successfully")
        void shouldChainMultipleOperationsSuccessfully() {
            Try<String> result = Try.of(() -> "42")
                .map(Integer::parseInt)
                .map(i -> i * 2)
                .map(i -> "Result: " + i);
            
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getOrElse("")).isEqualTo("Result: 84");
        }
        
        @Test
        @DisplayName("Should short-circuit on first exception")
        void shouldShortCircuitOnFirstException() {
            Try<String> result = Try.of(() -> "not a number")
                .map(Integer::parseInt)  // This will fail
                .map(i -> i * 2)  // This should not execute
                .map(i -> "Result: " + i);  // This should not execute
            
            assertThat(result.isFailure()).isTrue();
            assertThat(result.getException().get()).isInstanceOf(NumberFormatException.class);
        }
        
        @Test
        @DisplayName("Should recover from exceptions in chain")
        void shouldRecoverFromExceptionsInChain() {
            Try<String> result = Try.of(() -> "not a number")
                .map(Integer::parseInt)
                .recover(ex -> 0)  // Recover with default value
                .map(i -> i * 2)
                .map(i -> "Result: " + i);
            
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getOrElse("")).isEqualTo("Result: 0");
        }
        
        @Test
        @DisplayName("Should compose with flatMap for dependent operations")
        void shouldComposeWithFlatMapForDependentOperations() {
            Try<Integer> result = Try.of(() -> "42")
                .flatMap(s -> Try.of(() -> Integer.parseInt(s)))
                .flatMap(i -> i > 0 ? Try.success(i * 2) : Try.failure(new IllegalArgumentException("Non-positive")));
            
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getOrElse(0)).isEqualTo(84);
        }
        
        @Test
        @DisplayName("Should handle resource management pattern")
        void shouldHandleResourceManagementPattern() {
            // Simulate file operations that may fail
            Try<String> fileContent = Try.of(() -> "file content")
                .flatMap(content -> {
                    if (content.isEmpty()) {
                        return Try.failure(new IOException("Empty file"));
                    }
                    return Try.success(content.toUpperCase());
                })
                .recover(ex -> "DEFAULT CONTENT");
            
            assertThat(fileContent.isSuccess()).isTrue();
            assertThat(fileContent.getOrElse("")).isEqualTo("FILE CONTENT");
        }
        
        @Test
        @DisplayName("Should handle multiple exception types")
        void shouldHandleMultipleExceptionTypes() {
            Try<String> intermediate = Try.<String>failure(new IOException("IO error"))
                .recoverWith(ex -> {
                if (ex instanceof IOException) {
                    return Try.<String>failure(new NumberFormatException("Number error"));
                }
                return Try.failure(ex);
            });
            
            Try<String> result = intermediate.recover(ex -> {
                if (ex instanceof NumberFormatException) {
                    return "Recovered from number format error";
                }
                return "Recovered from: " + ex.getClass().getSimpleName();
            });
            
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getOrElse("")).isEqualTo("Recovered from number format error");
        }
    }
    
    @Nested
    @DisplayName("Equality and String Representation")
    class EqualityAndStringRepresentation {
        
        @Test
        @DisplayName("Should have equal Success instances with same value")
        void shouldHaveEqualSuccessInstancesWithSameValue() {
            Try<String> try1 = Try.success("test");
            Try<String> try2 = Try.success("test");
            
            assertThat(try1).isEqualTo(try2);
            assertThat(try1.hashCode()).isEqualTo(try2.hashCode());
        }
        
        @Test
        @DisplayName("Should have unequal Success instances with different values")
        void shouldHaveUnequalSuccessInstancesWithDifferentValues() {
            Try<String> try1 = Try.success("test1");
            Try<String> try2 = Try.success("test2");
            
            assertThat(try1).isNotEqualTo(try2);
        }
        
        @Test
        @DisplayName("Should have equal Failure instances with same exception")
        void shouldHaveEqualFailureInstancesWithSameException() {
            RuntimeException exception = new RuntimeException("error");
            Try<String> try1 = Try.failure(exception);
            Try<String> try2 = Try.failure(exception);
            
            assertThat(try1).isEqualTo(try2);
            assertThat(try1.hashCode()).isEqualTo(try2.hashCode());
        }
        
        @Test
        @DisplayName("Should have different Success and Failure instances")
        void shouldHaveDifferentSuccessAndFailureInstances() {
            Try<String> success = Try.success("test");
            Try<String> failure = Try.failure(new RuntimeException("error"));
            
            assertThat(success).isNotEqualTo(failure);
        }
        
        @Test
        @DisplayName("Should have meaningful string representation for Success")
        void shouldHaveMeaningfulStringRepresentationForSuccess() {
            Try<String> tryResult = Try.success("test");
            
            assertThat(tryResult.toString()).contains("Success");
            assertThat(tryResult.toString()).contains("test");
        }
        
        @Test
        @DisplayName("Should have meaningful string representation for Failure")
        void shouldHaveMeaningfulStringRepresentationForFailure() {
            Try<String> tryResult = Try.failure(new RuntimeException("error"));
            
            assertThat(tryResult.toString()).contains("Failure");
            assertThat(tryResult.toString()).contains("RuntimeException");
        }
    }
    
    @Nested
    @DisplayName("Real World Use Cases")
    class RealWorldUseCases {
        
        @Test
        @DisplayName("Should handle file parsing scenario")
        void shouldHandleFileParsingScenario() {
            // Simulate parsing a configuration file
            Try<Integer> configValue = Try.of(() -> "123")  // Simulate file reading
                .map(String::trim)
                .flatMap(content -> Try.of(() -> Integer.parseInt(content)))
                .recover(ex -> {
                    if (ex instanceof NumberFormatException) {
                        return 100; // Default value
                    }
                    throw new RuntimeException("Unexpected error", ex);
                });
            
            assertThat(configValue.isSuccess()).isTrue();
            assertThat(configValue.getOrElse(0)).isEqualTo(123);
        }
        
        @Test
        @DisplayName("Should handle API call scenario")
        void shouldHandleApiCallScenario() {
            // Simulate an API call that might fail
            Try<String> intermediate = Try.<String>failure(new IOException("Connection timeout"))
                .recoverWith(ex -> {
                if (ex instanceof IOException) {
                    // Retry with fallback API
                    return Try.of(() -> "fallback response");
                }
                return Try.failure(ex);
            });
            
            Try<String> apiResponse = intermediate.recover(ex -> "offline mode");
            
            assertThat(apiResponse.isSuccess()).isTrue();
            assertThat(apiResponse.getOrElse("")).isEqualTo("fallback response");
        }
        
        @Test
        @DisplayName("Should handle database transaction scenario")
        void shouldHandleDatabaseTransactionScenario() {
            // Simulate database operations
            Try<String> transaction = Try.of(() -> "start transaction")
                .flatMap(tx -> Try.of(() -> {
                    // Simulate successful operation
                    return "data inserted";
                }))
                .flatMap(result -> Try.of(() -> {
                    // Simulate commit
                    return "transaction committed";
                }))
                .recoverWith(ex -> {
                    // Simulate rollback on any error
                    return Try.run(() -> {
                        // Rollback logic here
                    }).map(v -> "transaction rolled back");
                });
            
            assertThat(transaction.isSuccess()).isTrue();
            assertThat(transaction.getOrElse("")).isEqualTo("transaction committed");
        }
        
        @Test
        @DisplayName("Should handle validation and transformation pipeline")
        void shouldHandleValidationAndTransformationPipeline() {
            record UserInput(String name, String ageStr, String email) {}
            record User(String name, int age, String email) {}
            
            UserInput input = new UserInput("John", "25", "john@example.com");
            
            Try<User> user = Try.of(() -> input)
                .flatMap(inp -> {
                    if (inp.name() == null || inp.name().trim().isEmpty()) {
                        return Try.failure(new IllegalArgumentException("Name is required"));
                    }
                    return Try.success(inp);
                })
                .flatMap(inp -> Try.of(() -> Integer.parseInt(inp.ageStr()))
                    .map(age -> new UserInput(inp.name(), inp.ageStr(), inp.email())))
                .flatMap(inp -> {
                    if (!inp.email().contains("@")) {
                        return Try.failure(new IllegalArgumentException("Invalid email"));
                    }
                    return Try.success(new User(inp.name(), Integer.parseInt(inp.ageStr()), inp.email()));
                });
            
            assertThat(user.isSuccess()).isTrue();
            User defaultUser = new User("", 0, "");
            assertThat(user.getOrElse(defaultUser).name()).isEqualTo("John");
            assertThat(user.getOrElse(defaultUser).age()).isEqualTo(25);
            assertThat(user.getOrElse(defaultUser).email()).isEqualTo("john@example.com");
        }
    }
}