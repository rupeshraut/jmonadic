package org.jmonadic.patterns;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Either Pattern Tests")
class EitherTest {
    
    @Nested
    @DisplayName("Construction")
    class Construction {
        
        @Test
        @DisplayName("Should create Left with value")
        void shouldCreateLeftWithValue() {
            String error = "error message";
            Either<String, Integer> either = Either.left(error);
            
            assertThat(either.isLeft()).isTrue();
            assertThat(either.isRight()).isFalse();
            assertThat(either.getLeft()).isEqualTo(error);
        }
        
        @Test
        @DisplayName("Should create Right with value")
        void shouldCreateRightWithValue() {
            Integer value = 42;
            Either<String, Integer> either = Either.right(value);
            
            assertThat(either.isRight()).isTrue();
            assertThat(either.isLeft()).isFalse();
            assertThat(either.getRight()).isEqualTo(value);
        }
        
        @Test
        @DisplayName("Should reject null value in Left")
        void shouldRejectNullValueInLeft() {
            assertThatThrownBy(() -> Either.left(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("Left value cannot be null");
        }
        
        @Test
        @DisplayName("Should reject null value in Right")
        void shouldRejectNullValueInRight() {
            assertThatThrownBy(() -> Either.right(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("Right value cannot be null");
        }
        
        @Test
        @DisplayName("Should create Right from successful supplier")
        void shouldCreateRightFromSuccessfulSupplier() {
            Either<Exception, String> either = Either.of(() -> "success");
            
            assertThat(either.isRight()).isTrue();
            assertThat(either.getRight()).isEqualTo("success");
        }
        
        @Test
        @DisplayName("Should create Left from failing supplier")
        void shouldCreateLeftFromFailingSupplier() {
            RuntimeException exception = new RuntimeException("failure");
            Either<Exception, String> either = Either.of(() -> {
                throw exception;
            });
            
            assertThat(either.isLeft()).isTrue();
            assertThat(either.getLeft()).isEqualTo(exception);
        }
        
        @Test
        @DisplayName("Should create Right from non-null value with ofNullable")
        void shouldCreateRightFromNonNullValueWithOfNullable() {
            String value = "test";
            Either<String, String> either = Either.ofNullable(value, () -> "error");
            
            assertThat(either.isRight()).isTrue();
            assertThat(either.getRight()).isEqualTo(value);
        }
        
        @Test
        @DisplayName("Should create Left from null value with ofNullable")
        void shouldCreateLeftFromNullValueWithOfNullable() {
            Either<String, String> either = Either.ofNullable(null, () -> "null error");
            
            assertThat(either.isLeft()).isTrue();
            assertThat(either.getLeft()).isEqualTo("null error");
        }
    }
    
    @Nested
    @DisplayName("Access Methods")
    class AccessMethods {
        
        @Test
        @DisplayName("Should throw when getting Left value from Right")
        void shouldThrowWhenGettingLeftValueFromRight() {
            Either<String, Integer> either = Either.right(42);
            
            assertThatThrownBy(either::getLeft)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Cannot get left value from Right");
        }
        
        @Test
        @DisplayName("Should throw when getting Right value from Left")
        void shouldThrowWhenGettingRightValueFromLeft() {
            Either<String, Integer> either = Either.left("error");
            
            assertThatThrownBy(either::getRight)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Cannot get right value from Left");
        }
        
        @Test
        @DisplayName("Should return Right value with getOrElse for Right")
        void shouldReturnRightValueWithGetOrElseForRight() {
            Either<String, Integer> either = Either.right(42);
            Integer result = either.getOrElse(0);
            
            assertThat(result).isEqualTo(42);
        }
        
        @Test
        @DisplayName("Should return default with getOrElse for Left")
        void shouldReturnDefaultWithGetOrElseForLeft() {
            Either<String, Integer> either = Either.left("error");
            Integer result = either.getOrElse(0);
            
            assertThat(result).isEqualTo(0);
        }
        
        @Test
        @DisplayName("Should return Right value with getOrElse function for Right")
        void shouldReturnRightValueWithGetOrElseFunctionForRight() {
            Either<String, Integer> either = Either.right(42);
            Integer result = either.getOrElse(error -> error.length());
            
            assertThat(result).isEqualTo(42);
        }
        
        @Test
        @DisplayName("Should apply function with getOrElse for Left")
        void shouldApplyFunctionWithGetOrElseForLeft() {
            Either<String, Integer> either = Either.left("error");
            Integer result = either.getOrElse(error -> error.length());
            
            assertThat(result).isEqualTo(5);
        }
    }
    
    @Nested
    @DisplayName("Transformation")
    class Transformation {
        
        @Test
        @DisplayName("Should map Right value")
        void shouldMapRightValue() {
            Either<String, Integer> either = Either.right(42);
            Either<String, String> mapped = either.map(i -> "Value: " + i);
            
            assertThat(mapped.isRight()).isTrue();
            assertThat(mapped.getRight()).isEqualTo("Value: 42");
        }
        
        @Test
        @DisplayName("Should not map Left value")
        void shouldNotMapLeftValue() {
            Either<String, Integer> either = Either.left("error");
            Either<String, String> mapped = either.map(i -> "Value: " + i);
            
            assertThat(mapped.isLeft()).isTrue();
            assertThat(mapped.getLeft()).isEqualTo("error");
        }
        
        @Test
        @DisplayName("Should map Left value with mapLeft")
        void shouldMapLeftValueWithMapLeft() {
            Either<String, Integer> either = Either.left("error");
            Either<Integer, Integer> mapped = either.mapLeft(String::length);
            
            assertThat(mapped.isLeft()).isTrue();
            assertThat(mapped.getLeft()).isEqualTo(5);
        }
        
        @Test
        @DisplayName("Should not map Right value with mapLeft")
        void shouldNotMapRightValueWithMapLeft() {
            Either<String, Integer> either = Either.right(42);
            Either<Integer, Integer> mapped = either.mapLeft(String::length);
            
            assertThat(mapped.isRight()).isTrue();
            assertThat(mapped.getRight()).isEqualTo(42);
        }
        
        @Test
        @DisplayName("Should flatMap Right value")
        void shouldFlatMapRightValue() {
            Either<String, Integer> either = Either.right(42);
            Either<String, String> flatMapped = either.flatMap(i -> Either.right("Value: " + i));
            
            assertThat(flatMapped.isRight()).isTrue();
            assertThat(flatMapped.getRight()).isEqualTo("Value: 42");
        }
        
        @Test
        @DisplayName("Should flatMap Right to Left")
        void shouldFlatMapRightToLeft() {
            Either<String, Integer> either = Either.right(42);
            Either<String, String> flatMapped = either.flatMap(i -> Either.left("converted to error"));
            
            assertThat(flatMapped.isLeft()).isTrue();
            assertThat(flatMapped.getLeft()).isEqualTo("converted to error");
        }
        
        @Test
        @DisplayName("Should not flatMap Left value")
        void shouldNotFlatMapLeftValue() {
            Either<String, Integer> either = Either.left("error");
            Either<String, String> flatMapped = either.flatMap(i -> Either.right("Value: " + i));
            
            assertThat(flatMapped.isLeft()).isTrue();
            assertThat(flatMapped.getLeft()).isEqualTo("error");
        }
        
        @Test
        @DisplayName("Should filter Right with passing predicate")
        void shouldFilterRightWithPassingPredicate() {
            Either<String, Integer> either = Either.right(42);
            Either<String, Integer> filtered = either.filter(i -> i > 40, () -> "too small");
            
            assertThat(filtered.isRight()).isTrue();
            assertThat(filtered.getRight()).isEqualTo(42);
        }
        
        @Test
        @DisplayName("Should filter Right with failing predicate")
        void shouldFilterRightWithFailingPredicate() {
            Either<String, Integer> either = Either.right(42);
            Either<String, Integer> filtered = either.filter(i -> i > 50, () -> "too small");
            
            assertThat(filtered.isLeft()).isTrue();
            assertThat(filtered.getLeft()).isEqualTo("too small");
        }
        
        @Test
        @DisplayName("Should not filter Left value")
        void shouldNotFilterLeftValue() {
            Either<String, Integer> either = Either.left("error");
            Either<String, Integer> filtered = either.filter(i -> true, () -> "filter error");
            
            assertThat(filtered.isLeft()).isTrue();
            assertThat(filtered.getLeft()).isEqualTo("error");
        }
    }
    
    @Nested
    @DisplayName("Side Effects")
    class SideEffects {
        
        @Test
        @DisplayName("Should execute left consumer for Left")
        void shouldExecuteLeftConsumerForLeft() {
            Either<String, Integer> either = Either.left("error");
            AtomicBoolean leftCalled = new AtomicBoolean(false);
            AtomicBoolean rightCalled = new AtomicBoolean(false);
            
            Either<String, Integer> result = either.peek(
                left -> leftCalled.set(true),
                right -> rightCalled.set(true)
            );
            
            assertThat(result).isSameAs(either);
            assertThat(leftCalled.get()).isTrue();
            assertThat(rightCalled.get()).isFalse();
        }
        
        @Test
        @DisplayName("Should execute right consumer for Right")
        void shouldExecuteRightConsumerForRight() {
            Either<String, Integer> either = Either.right(42);
            AtomicBoolean leftCalled = new AtomicBoolean(false);
            AtomicBoolean rightCalled = new AtomicBoolean(false);
            
            Either<String, Integer> result = either.peek(
                left -> leftCalled.set(true),
                right -> rightCalled.set(true)
            );
            
            assertThat(result).isSameAs(either);
            assertThat(leftCalled.get()).isFalse();
            assertThat(rightCalled.get()).isTrue();
        }
        
        @Test
        @DisplayName("Should execute peekLeft for Left")
        void shouldExecutePeekLeftForLeft() {
            Either<String, Integer> either = Either.left("error");
            AtomicInteger counter = new AtomicInteger(0);
            
            Either<String, Integer> result = either.peekLeft(left -> counter.incrementAndGet());
            
            assertThat(result).isSameAs(either);
            assertThat(counter.get()).isEqualTo(1);
        }
        
        @Test
        @DisplayName("Should not execute peekLeft for Right")
        void shouldNotExecutePeekLeftForRight() {
            Either<String, Integer> either = Either.right(42);
            AtomicInteger counter = new AtomicInteger(0);
            
            Either<String, Integer> result = either.peekLeft(left -> counter.incrementAndGet());
            
            assertThat(result).isSameAs(either);
            assertThat(counter.get()).isEqualTo(0);
        }
        
        @Test
        @DisplayName("Should execute peekRight for Right")
        void shouldExecutePeekRightForRight() {
            Either<String, Integer> either = Either.right(42);
            AtomicInteger counter = new AtomicInteger(0);
            
            Either<String, Integer> result = either.peekRight(right -> counter.addAndGet(right));
            
            assertThat(result).isSameAs(either);
            assertThat(counter.get()).isEqualTo(42);
        }
        
        @Test
        @DisplayName("Should not execute peekRight for Left")
        void shouldNotExecutePeekRightForLeft() {
            Either<String, Integer> either = Either.left("error");
            AtomicInteger counter = new AtomicInteger(0);
            
            Either<String, Integer> result = either.peekRight(right -> counter.incrementAndGet());
            
            assertThat(result).isSameAs(either);
            assertThat(counter.get()).isEqualTo(0);
        }
    }
    
    @Nested
    @DisplayName("Folding")
    class Folding {
        
        @Test
        @DisplayName("Should fold Left to left function result")
        void shouldFoldLeftToLeftFunctionResult() {
            Either<String, Integer> either = Either.left("error");
            
            String result = either.fold(
                left -> "Error: " + left,
                right -> "Value: " + right
            );
            
            assertThat(result).isEqualTo("Error: error");
        }
        
        @Test
        @DisplayName("Should fold Right to right function result")
        void shouldFoldRightToRightFunctionResult() {
            Either<String, Integer> either = Either.right(42);
            
            String result = either.fold(
                left -> "Error: " + left,
                right -> "Value: " + right
            );
            
            assertThat(result).isEqualTo("Value: 42");
        }
    }
    
    @Nested
    @DisplayName("Utilities")
    class Utilities {
        
        @Test
        @DisplayName("Should swap Left to Right")
        void shouldSwapLeftToRight() {
            Either<String, Integer> either = Either.left("error");
            Either<Integer, String> swapped = either.swap();
            
            assertThat(swapped.isRight()).isTrue();
            assertThat(swapped.getRight()).isEqualTo("error");
        }
        
        @Test
        @DisplayName("Should swap Right to Left")
        void shouldSwapRightToLeft() {
            Either<String, Integer> either = Either.right(42);
            Either<Integer, String> swapped = either.swap();
            
            assertThat(swapped.isLeft()).isTrue();
            assertThat(swapped.getLeft()).isEqualTo(42);
        }
    }
    
    @Nested
    @DisplayName("Conversion")
    class Conversion {
        
        @Test
        @DisplayName("Should convert Right to Result success")
        void shouldConvertRightToResultSuccess() {
            Either<String, Integer> either = Either.right(42);
            Result<Integer, String> result = either.toResult();
            
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getValue()).isEqualTo(42);
        }
        
        @Test
        @DisplayName("Should convert Left to Result failure")
        void shouldConvertLeftToResultFailure() {
            Either<String, Integer> either = Either.left("error");
            Result<Integer, String> result = either.toResult();
            
            assertThat(result.isFailure()).isTrue();
            assertThat(result.getError()).isEqualTo("error");
        }
    }
    
    @Nested
    @DisplayName("Chaining and Composition")
    class ChainingAndComposition {
        
        @Test
        @DisplayName("Should chain multiple operations successfully")
        void shouldChainMultipleOperationsSuccessfully() {
            Either<String, String> result = Either.<String, String>right("42")
                .map(Integer::parseInt)
                .filter(i -> i > 40, () -> "too small")
                .map(i -> i * 2)
                .map(i -> "Result: " + i);
            
            assertThat(result.isRight()).isTrue();
            assertThat(result.getRight()).isEqualTo("Result: 84");
        }
        
        @Test
        @DisplayName("Should short-circuit on first error")
        void shouldShortCircuitOnFirstError() {
            Either<String, String> result = Either.<String, String>right("42")
                .map(Integer::parseInt)
                .filter(i -> i > 50, () -> "too small")  // This will fail
                .map(i -> i * 2)  // This should not execute
                .map(i -> "Result: " + i);  // This should not execute
            
            assertThat(result.isLeft()).isTrue();
            assertThat(result.getLeft()).isEqualTo("too small");
        }
        
        @Test
        @DisplayName("Should compose with flatMap for dependent operations")
        void shouldComposeWithFlatMapForDependentOperations() {
            Either<String, Integer> result = Either.<String, String>right("42")
                .flatMap(s -> {
                    try {
                        return Either.<String, Integer>right(Integer.parseInt(s));
                    } catch (NumberFormatException e) {
                        return Either.<String, Integer>left("Invalid number: " + s);
                    }
                })
                .flatMap(i -> i > 0 ? Either.<String, Integer>right(i * 2) : Either.<String, Integer>left("Non-positive number"));
            
            assertThat(result.isRight()).isTrue();
            assertThat(result.getRight()).isEqualTo(84);
        }
        
        @Test
        @DisplayName("Should handle validation pipeline")
        void shouldHandleValidationPipeline() {
            record User(String name, int age, String email) {}
            
            Either<String, User> result = Either.<String, User>right(new User("John", 25, "john@example.com"))
                .filter(u -> u.name() != null && !u.name().trim().isEmpty(), () -> "Name is required")
                .filter(u -> u.age() >= 0 && u.age() <= 150, () -> "Invalid age")
                .filter(u -> u.email() != null && u.email().contains("@"), () -> "Invalid email");
            
            assertThat(result.isRight()).isTrue();
            assertThat(result.getRight().name()).isEqualTo("John");
        }
        
        @Test
        @DisplayName("Should fail validation pipeline on first invalid field")
        void shouldFailValidationPipelineOnFirstInvalidField() {
            record User(String name, int age, String email) {}
            
            Either<String, User> result = Either.<String, User>right(new User("", 25, "john@example.com"))
                .filter(u -> u.name() != null && !u.name().trim().isEmpty(), () -> "Name is required")
                .filter(u -> u.age() >= 0 && u.age() <= 150, () -> "Invalid age")
                .filter(u -> u.email() != null && u.email().contains("@"), () -> "Invalid email");
            
            assertThat(result.isLeft()).isTrue();
            assertThat(result.getLeft()).isEqualTo("Name is required");
        }
    }
    
    @Nested
    @DisplayName("Equality and String Representation")
    class EqualityAndStringRepresentation {
        
        @Test
        @DisplayName("Should have equal Left instances with same value")
        void shouldHaveEqualLeftInstancesWithSameValue() {
            Either<String, Integer> either1 = Either.left("error");
            Either<String, Integer> either2 = Either.left("error");
            
            assertThat(either1).isEqualTo(either2);
            assertThat(either1.hashCode()).isEqualTo(either2.hashCode());
        }
        
        @Test
        @DisplayName("Should have unequal Left instances with different values")
        void shouldHaveUnequalLeftInstancesWithDifferentValues() {
            Either<String, Integer> either1 = Either.left("error1");
            Either<String, Integer> either2 = Either.left("error2");
            
            assertThat(either1).isNotEqualTo(either2);
        }
        
        @Test
        @DisplayName("Should have equal Right instances with same value")
        void shouldHaveEqualRightInstancesWithSameValue() {
            Either<String, Integer> either1 = Either.right(42);
            Either<String, Integer> either2 = Either.right(42);
            
            assertThat(either1).isEqualTo(either2);
            assertThat(either1.hashCode()).isEqualTo(either2.hashCode());
        }
        
        @Test
        @DisplayName("Should have unequal Right instances with different values")
        void shouldHaveUnequalRightInstancesWithDifferentValues() {
            Either<String, Integer> either1 = Either.right(42);
            Either<String, Integer> either2 = Either.right(24);
            
            assertThat(either1).isNotEqualTo(either2);
        }
        
        @Test
        @DisplayName("Should have different Left and Right instances")
        void shouldHaveDifferentLeftAndRightInstances() {
            Either<String, Integer> left = Either.left("error");
            Either<String, Integer> right = Either.right(42);
            
            assertThat(left).isNotEqualTo(right);
        }
        
        @Test
        @DisplayName("Should have meaningful string representation for Left")
        void shouldHaveMeaningfulStringRepresentationForLeft() {
            Either<String, Integer> either = Either.left("error");
            
            assertThat(either.toString()).contains("Left");
            assertThat(either.toString()).contains("error");
        }
        
        @Test
        @DisplayName("Should have meaningful string representation for Right")
        void shouldHaveMeaningfulStringRepresentationForRight() {
            Either<String, Integer> either = Either.right(42);
            
            assertThat(either.toString()).contains("Right");
            assertThat(either.toString()).contains("42");
        }
    }
    
    @Nested
    @DisplayName("Error Handling Edge Cases")
    class ErrorHandlingEdgeCases {
        
        @Test
        @DisplayName("Should handle exceptions in map function")
        void shouldHandleExceptionsInMapFunction() {
            Either<String, String> either = Either.right("42");
            
            // This test verifies that if the map function throws, it's not caught
            // The Either pattern doesn't automatically catch exceptions in transformations
            assertThatThrownBy(() -> either.map(s -> {
                throw new RuntimeException("map failed");
            })).isInstanceOf(RuntimeException.class)
              .hasMessage("map failed");
        }
        
        @Test
        @DisplayName("Should handle exceptions in flatMap function")
        void shouldHandleExceptionsInFlatMapFunction() {
            Either<String, String> either = Either.right("42");
            
            assertThatThrownBy(() -> either.flatMap(s -> {
                throw new RuntimeException("flatMap failed");
            })).isInstanceOf(RuntimeException.class)
              .hasMessage("flatMap failed");
        }
        
        @Test
        @DisplayName("Should handle exceptions in filter predicate")
        void shouldHandleExceptionsInFilterPredicate() {
            Either<String, String> either = Either.right("42");
            
            assertThatThrownBy(() -> either.filter(s -> {
                throw new RuntimeException("predicate failed");
            }, () -> "filter error")).isInstanceOf(RuntimeException.class)
                                      .hasMessage("predicate failed");
        }
        
        @Test
        @DisplayName("Should handle exceptions in fold functions")
        void shouldHandleExceptionsInFoldFunctions() {
            Either<String, Integer> either = Either.right(42);
            
            assertThatThrownBy(() -> either.fold(
                left -> "Error: " + left,
                right -> { throw new RuntimeException("fold failed"); }
            )).isInstanceOf(RuntimeException.class)
              .hasMessage("fold failed");
        }
    }
}