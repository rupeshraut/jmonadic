package org.jmonadic.patterns;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Validation Pattern Tests")
class ValidationTest {
    
    @Nested
    @DisplayName("Construction")
    class Construction {
        
        @Test
        @DisplayName("Should create Valid with value")
        void shouldCreateValidWithValue() {
            String value = "test";
            Validation<String, String> validation = Validation.valid(value);
            
            assertThat(validation.isValid()).isTrue();
            assertThat(validation.isInvalid()).isFalse();
            assertThat(validation.getValue()).isEqualTo(value);
        }
        
        @Test
        @DisplayName("Should create Invalid with single error")
        void shouldCreateInvalidWithSingleError() {
            String error = "validation failed";
            Validation<String, String> validation = Validation.invalid(error);
            
            assertThat(validation.isInvalid()).isTrue();
            assertThat(validation.isValid()).isFalse();
            assertThat(validation.getErrors()).containsExactly(error);
        }
        
        @Test
        @DisplayName("Should create Invalid with multiple errors from list")
        void shouldCreateInvalidWithMultipleErrorsFromList() {
            List<String> errors = Arrays.asList("error1", "error2", "error3");
            Validation<String, String> validation = Validation.invalid(errors);
            
            assertThat(validation.isInvalid()).isTrue();
            assertThat(validation.getErrors()).containsExactly("error1", "error2", "error3");
        }
        
        @Test
        @DisplayName("Should create Invalid with multiple errors from varargs")
        void shouldCreateInvalidWithMultipleErrorsFromVarargs() {
            Validation<String, String> validation = Validation.invalid("error1", "error2", "error3");
            
            assertThat(validation.isInvalid()).isTrue();
            assertThat(validation.getErrors()).containsExactly("error1", "error2", "error3");
        }
        
        @Test
        @DisplayName("Should reject null error in invalid()")
        void shouldRejectNullErrorInInvalid() {
            assertThatThrownBy(() -> Validation.invalid((String) null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("Error cannot be null");
        }
        
        @Test
        @DisplayName("Should reject null error list in invalid()")
        void shouldRejectNullErrorListInInvalid() {
            assertThatThrownBy(() -> Validation.invalid((List<String>) null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("Errors cannot be null");
        }
        
        @Test
        @DisplayName("Should reject empty error list in invalid()")
        void shouldRejectEmptyErrorListInInvalid() {
            assertThatThrownBy(() -> Validation.invalid(Collections.emptyList()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Error list cannot be empty");
        }
        
        @Test
        @DisplayName("Should reject empty varargs in invalid()")
        void shouldRejectEmptyVarargsInInvalid() {
            assertThatThrownBy(() -> Validation.invalid())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Must provide at least one error");
        }
        
        @Test
        @DisplayName("Should create Valid from successful supplier")
        void shouldCreateValidFromSuccessfulSupplier() {
            Validation<String, String> validation = Validation.of(
                () -> "success",
                Exception::getMessage
            );
            
            assertThat(validation.isValid()).isTrue();
            assertThat(validation.getValue()).isEqualTo("success");
        }
        
        @Test
        @DisplayName("Should create Invalid from failing supplier")
        void shouldCreateInvalidFromFailingSupplier() {
            Validation<String, String> validation = Validation.of(
                () -> { throw new RuntimeException("failure"); },
                Exception::getMessage
            );
            
            assertThat(validation.isInvalid()).isTrue();
            assertThat(validation.getErrors()).containsExactly("failure");
        }
    }
    
    @Nested
    @DisplayName("Access Methods")
    class AccessMethods {
        
        @Test
        @DisplayName("Should throw when getting value from Invalid")
        void shouldThrowWhenGettingValueFromInvalid() {
            Validation<String, String> validation = Validation.invalid("error");
            
            assertThatThrownBy(validation::getValue)
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessage("Cannot get value from Invalid");
        }
        
        @Test
        @DisplayName("Should throw when getting errors from Valid")
        void shouldThrowWhenGettingErrorsFromValid() {
            Validation<String, String> validation = Validation.valid("value");
            
            assertThatThrownBy(validation::getErrors)
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessage("Cannot get errors from Valid");
        }
    }
    
    @Nested
    @DisplayName("Transformation")
    class Transformation {
        
        @Test
        @DisplayName("Should map Valid value")
        void shouldMapValidValue() {
            Validation<String, String> validation = Validation.valid("test");
            Validation<String, Integer> mapped = validation.map(String::length);
            
            assertThat(mapped.isValid()).isTrue();
            assertThat(mapped.getValue()).isEqualTo(4);
        }
        
        @Test
        @DisplayName("Should map Invalid to Invalid")
        void shouldMapInvalidToInvalid() {
            Validation<String, String> validation = Validation.invalid("error");
            Validation<String, Integer> mapped = validation.map(String::length);
            
            assertThat(mapped.isInvalid()).isTrue();
            assertThat(mapped.getErrors()).containsExactly("error");
        }
        
        @Test
        @DisplayName("Should map errors for Invalid")
        void shouldMapErrorsForInvalid() {
            Validation<String, String> validation = Validation.invalid("error");
            Validation<Integer, String> mapped = validation.mapError(String::length);
            
            assertThat(mapped.isInvalid()).isTrue();
            assertThat(mapped.getErrors()).containsExactly(5);
        }
        
        @Test
        @DisplayName("Should not map errors for Valid")
        void shouldNotMapErrorsForValid() {
            Validation<String, String> validation = Validation.valid("value");
            Validation<Integer, String> mapped = validation.mapError(String::length);
            
            assertThat(mapped.isValid()).isTrue();
            assertThat(mapped.getValue()).isEqualTo("value");
        }
        
        @Test
        @DisplayName("Should map error list for Invalid")
        void shouldMapErrorListForInvalid() {
            Validation<String, String> validation = Validation.invalid("error1", "error2");
            Validation<String, String> mapped = validation.mapErrors(errors -> 
                errors.stream().map(e -> e.toUpperCase()).collect(java.util.stream.Collectors.toList())
            );
            
            assertThat(mapped.isInvalid()).isTrue();
            assertThat(mapped.getErrors()).containsExactly("ERROR1", "ERROR2");
        }
    }
    
    @Nested
    @DisplayName("Error Accumulation")
    class ErrorAccumulation {
        
        @Test
        @DisplayName("Should combine two Valid validations")
        void shouldCombineTwoValidValidations() {
            Validation<String, String> first = Validation.valid("Hello");
            Validation<String, String> second = Validation.valid("World");
            
            Validation<String, String> result = first.combine(second, (a, b) -> a + " " + b);
            
            assertThat(result.isValid()).isTrue();
            assertThat(result.getValue()).isEqualTo("Hello World");
        }
        
        @Test
        @DisplayName("Should accumulate errors from two Invalid validations")
        void shouldAccumulateErrorsFromTwoInvalidValidations() {
            Validation<String, String> first = Validation.invalid("error1");
            Validation<String, String> second = Validation.invalid("error2");
            
            Validation<String, String> result = first.combine(second, (a, b) -> a + " " + b);
            
            assertThat(result.isInvalid()).isTrue();
            assertThat(result.getErrors()).containsExactly("error1", "error2");
        }
        
        @Test
        @DisplayName("Should return errors when combining Valid with Invalid")
        void shouldReturnErrorsWhenCombiningValidWithInvalid() {
            Validation<String, String> valid = Validation.valid("value");
            Validation<String, String> invalid = Validation.invalid("error");
            
            Validation<String, String> result = valid.combine(invalid, (a, b) -> a + " " + b);
            
            assertThat(result.isInvalid()).isTrue();
            assertThat(result.getErrors()).containsExactly("error");
        }
        
        @Test
        @DisplayName("Should accumulate multiple errors from both validations")
        void shouldAccumulateMultipleErrorsFromBothValidations() {
            Validation<String, String> first = Validation.invalid("error1", "error2");
            Validation<String, String> second = Validation.invalid("error3", "error4");
            
            Validation<String, String> result = first.combine(second, (a, b) -> a + " " + b);
            
            assertThat(result.isInvalid()).isTrue();
            assertThat(result.getErrors()).containsExactly("error1", "error2", "error3", "error4");
        }
        
        @Test
        @DisplayName("Should apply function validation to value")
        void shouldApplyFunctionValidationToValue() {
            Validation<String, String> value = Validation.valid("test");
            Validation<String, java.util.function.Function<String, Integer>> function = 
                Validation.valid(String::length);
            
            Validation<String, Integer> result = value.apply(function);
            
            assertThat(result.isValid()).isTrue();
            assertThat(result.getValue()).isEqualTo(4);
        }
        
        @Test
        @DisplayName("Should accumulate errors in apply operation")
        void shouldAccumulateErrorsInApplyOperation() {
            Validation<String, String> value = Validation.invalid("value_error");
            Validation<String, java.util.function.Function<String, Integer>> function = 
                Validation.invalid("function_error");
            
            Validation<String, Integer> result = value.apply(function);
            
            assertThat(result.isInvalid()).isTrue();
            assertThat(result.getErrors()).containsExactly("function_error", "value_error");
        }
    }
    
    @Nested
    @DisplayName("Validation Operations")
    class ValidationOperations {
        
        @Test
        @DisplayName("Should ensure Valid value passes predicate")
        void shouldEnsureValidValuePassesPredicate() {
            Validation<String, String> validation = Validation.valid("test");
            Validation<String, String> result = validation.ensure(s -> s.length() > 3, "too short");
            
            assertThat(result.isValid()).isTrue();
            assertThat(result.getValue()).isEqualTo("test");
        }
        
        @Test
        @DisplayName("Should ensure Valid value fails predicate")
        void shouldEnsureValidValueFailsPredicate() {
            Validation<String, String> validation = Validation.valid("hi");
            Validation<String, String> result = validation.ensure(s -> s.length() > 3, "too short");
            
            assertThat(result.isInvalid()).isTrue();
            assertThat(result.getErrors()).containsExactly("too short");
        }
        
        @Test
        @DisplayName("Should ensure Invalid remains Invalid")
        void shouldEnsureInvalidRemainsInvalid() {
            Validation<String, String> validation = Validation.invalid("original error");
            Validation<String, String> result = validation.ensure(s -> true, "new error");
            
            assertThat(result.isInvalid()).isTrue();
            assertThat(result.getErrors()).containsExactly("original error");
        }
        
        @Test
        @DisplayName("Should handle predicate exception in ensure")
        void shouldHandlePredicateExceptionInEnsure() {
            Validation<String, String> validation = Validation.valid("test");
            Validation<String, String> result = validation.ensure(s -> {
                throw new RuntimeException("predicate failed");
            }, "validation error");
            
            assertThat(result.isInvalid()).isTrue();
            assertThat(result.getErrors()).containsExactly("validation error");
        }
        
        @Test
        @DisplayName("Should ensure with validator returning no errors")
        void shouldEnsureWithValidatorReturningNoErrors() {
            Validation<String, String> validation = Validation.valid("test");
            Validation<String, String> result = validation.ensureWith(s -> Collections.emptyList());
            
            assertThat(result.isValid()).isTrue();
            assertThat(result.getValue()).isEqualTo("test");
        }
        
        @Test
        @DisplayName("Should ensure with validator returning errors")
        void shouldEnsureWithValidatorReturningErrors() {
            Validation<String, String> validation = Validation.valid("test");
            Validation<String, String> result = validation.ensureWith(s -> Arrays.asList("error1", "error2"));
            
            assertThat(result.isInvalid()).isTrue();
            assertThat(result.getErrors()).containsExactly("error1", "error2");
        }
        
        @Test
        @DisplayName("Should ensure with validator returning null")
        void shouldEnsureWithValidatorReturningNull() {
            Validation<String, String> validation = Validation.valid("test");
            Validation<String, String> result = validation.ensureWith(s -> null);
            
            assertThat(result.isValid()).isTrue();
            assertThat(result.getValue()).isEqualTo("test");
        }
    }
    
    @Nested
    @DisplayName("Terminal Operations")
    class TerminalOperations {
        
        @Test
        @DisplayName("Should return value with getOrElse for Valid")
        void shouldReturnValueWithGetOrElseForValid() {
            Validation<String, String> validation = Validation.valid("test");
            String result = validation.getOrElse("default");
            
            assertThat(result).isEqualTo("test");
        }
        
        @Test
        @DisplayName("Should return default with getOrElse for Invalid")
        void shouldReturnDefaultWithGetOrElseForInvalid() {
            Validation<String, String> validation = Validation.invalid("error");
            String result = validation.getOrElse("default");
            
            assertThat(result).isEqualTo("default");
        }
        
        @Test
        @DisplayName("Should return value with getOrElse supplier for Valid")
        void shouldReturnValueWithGetOrElseSupplierForValid() {
            Validation<String, String> validation = Validation.valid("test");
            AtomicBoolean supplierCalled = new AtomicBoolean(false);
            
            String result = validation.getOrElse(() -> {
                supplierCalled.set(true);
                return "default";
            });
            
            assertThat(result).isEqualTo("test");
            assertThat(supplierCalled.get()).isFalse();
        }
        
        @Test
        @DisplayName("Should call supplier with getOrElse for Invalid")
        void shouldCallSupplierWithGetOrElseForInvalid() {
            Validation<String, String> validation = Validation.invalid("error");
            AtomicBoolean supplierCalled = new AtomicBoolean(false);
            
            String result = validation.getOrElse(() -> {
                supplierCalled.set(true);
                return "default";
            });
            
            assertThat(result).isEqualTo("default");
            assertThat(supplierCalled.get()).isTrue();
        }
        
        @Test
        @DisplayName("Should fold Valid to success value")
        void shouldFoldValidToSuccessValue() {
            Validation<String, String> validation = Validation.valid("test");
            
            String result = validation.fold(
                errors -> "Invalid: " + String.join(", ", errors),
                value -> "Valid: " + value
            );
            
            assertThat(result).isEqualTo("Valid: test");
        }
        
        @Test
        @DisplayName("Should fold Invalid to error value")
        void shouldFoldInvalidToErrorValue() {
            Validation<String, String> validation = Validation.invalid("error1", "error2");
            
            String result = validation.fold(
                errors -> "Invalid: " + String.join(", ", errors),
                value -> "Valid: " + value
            );
            
            assertThat(result).isEqualTo("Invalid: error1, error2");
        }
    }
    
    @Nested
    @DisplayName("Side Effects")
    class SideEffects {
        
        @Test
        @DisplayName("Should execute peek action for Valid")
        void shouldExecutePeekActionForValid() {
            Validation<String, String> validation = Validation.valid("test");
            AtomicInteger counter = new AtomicInteger(0);
            
            Validation<String, String> result = validation.peek(s -> counter.incrementAndGet());
            
            assertThat(result).isSameAs(validation);
            assertThat(counter.get()).isEqualTo(1);
        }
        
        @Test
        @DisplayName("Should not execute peek action for Invalid")
        void shouldNotExecutePeekActionForInvalid() {
            Validation<String, String> validation = Validation.invalid("error");
            AtomicInteger counter = new AtomicInteger(0);
            
            Validation<String, String> result = validation.peek(s -> counter.incrementAndGet());
            
            assertThat(result).isSameAs(validation);
            assertThat(counter.get()).isEqualTo(0);
        }
        
        @Test
        @DisplayName("Should execute peekErrors action for Invalid")
        void shouldExecutePeekErrorsActionForInvalid() {
            Validation<String, String> validation = Validation.invalid("error1", "error2");
            AtomicInteger counter = new AtomicInteger(0);
            
            Validation<String, String> result = validation.peekErrors(errors -> counter.set(errors.size()));
            
            assertThat(result).isSameAs(validation);
            assertThat(counter.get()).isEqualTo(2);
        }
        
        @Test
        @DisplayName("Should not execute peekErrors action for Valid")
        void shouldNotExecutePeekErrorsActionForValid() {
            Validation<String, String> validation = Validation.valid("test");
            AtomicInteger counter = new AtomicInteger(0);
            
            Validation<String, String> result = validation.peekErrors(errors -> counter.incrementAndGet());
            
            assertThat(result).isSameAs(validation);
            assertThat(counter.get()).isEqualTo(0);
        }
    }
    
    @Nested
    @DisplayName("Conversion")
    class Conversion {
        
        @Test
        @DisplayName("Should convert Valid to Result success")
        void shouldConvertValidToResultSuccess() {
            Validation<String, String> validation = Validation.valid("test");
            Result<String, String> result = validation.toResult();
            
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getValue()).isEqualTo("test");
        }
        
        @Test
        @DisplayName("Should convert Invalid to Result failure with first error")
        void shouldConvertInvalidToResultFailureWithFirstError() {
            Validation<String, String> validation = Validation.invalid("error1", "error2");
            Result<String, String> result = validation.toResult();
            
            assertThat(result.isFailure()).isTrue();
            assertThat(result.getError()).isEqualTo("error1");
        }
        
        @Test
        @DisplayName("Should convert Invalid to Result with combined error")
        void shouldConvertInvalidToResultWithCombinedError() {
            Validation<String, String> validation = Validation.invalid("error1", "error2");
            Result<String, String> result = validation.toResult(errors -> String.join(", ", errors));
            
            assertThat(result.isFailure()).isTrue();
            assertThat(result.getError()).isEqualTo("error1, error2");
        }
        
        @Test
        @DisplayName("Should convert Valid to Either right")
        void shouldConvertValidToEitherRight() {
            Validation<String, String> validation = Validation.valid("test");
            Either<List<String>, String> either = validation.toEither();
            
            assertThat(either.isRight()).isTrue();
            assertThat(either.getRight()).isEqualTo("test");
        }
        
        @Test
        @DisplayName("Should convert Invalid to Either left")
        void shouldConvertInvalidToEitherLeft() {
            Validation<String, String> validation = Validation.invalid("error1", "error2");
            Either<List<String>, String> either = validation.toEither();
            
            assertThat(either.isLeft()).isTrue();
            assertThat(either.getLeft()).containsExactly("error1", "error2");
        }
        
        @Test
        @DisplayName("Should convert Valid to Option some")
        void shouldConvertValidToOptionSome() {
            Validation<String, String> validation = Validation.valid("test");
            Option<String> option = validation.toOption();
            
            assertThat(option.isSome()).isTrue();
            assertThat(option.get()).isEqualTo("test");
        }
        
        @Test
        @DisplayName("Should convert Invalid to Option none")
        void shouldConvertInvalidToOptionNone() {
            Validation<String, String> validation = Validation.invalid("error");
            Option<String> option = validation.toOption();
            
            assertThat(option.isNone()).isTrue();
        }
    }
    
    @Nested
    @DisplayName("Static Utilities")
    class StaticUtilities {
        
        @Test
        @DisplayName("Should sequence all Valid validations")
        void shouldSequenceAllValidValidations() {
            Validation<String, String> first = Validation.valid("first");
            Validation<String, String> second = Validation.valid("second");
            Validation<String, String> third = Validation.valid("third");
            
            Validation<String, List<String>> result = Validation.sequence(first, second, third);
            
            assertThat(result.isValid()).isTrue();
            assertThat(result.getValue()).containsExactly("first", "second", "third");
        }
        
        @Test
        @DisplayName("Should sequence with some Invalid validations")
        void shouldSequenceWithSomeInvalidValidations() {
            Validation<String, String> first = Validation.valid("first");
            Validation<String, String> second = Validation.invalid("error1");
            Validation<String, String> third = Validation.invalid("error2", "error3");
            
            Validation<String, List<String>> result = Validation.sequence(first, second, third);
            
            assertThat(result.isInvalid()).isTrue();
            assertThat(result.getErrors()).containsExactly("error1", "error2", "error3");
        }
        
        @Test
        @DisplayName("Should sequence empty list")
        void shouldSequenceEmptyList() {
            Validation<String, List<String>> result = Validation.sequence(Collections.emptyList());
            
            assertThat(result.isValid()).isTrue();
            assertThat(result.getValue()).isEmpty();
        }
    }
    
    @Nested
    @DisplayName("Equality and String Representation")
    class EqualityAndStringRepresentation {
        
        @Test
        @DisplayName("Should have equal Valid instances with same value")
        void shouldHaveEqualValidInstancesWithSameValue() {
            Validation<String, String> validation1 = Validation.valid("test");
            Validation<String, String> validation2 = Validation.valid("test");
            
            assertThat(validation1).isEqualTo(validation2);
            assertThat(validation1.hashCode()).isEqualTo(validation2.hashCode());
        }
        
        @Test
        @DisplayName("Should have unequal Valid instances with different values")
        void shouldHaveUnequalValidInstancesWithDifferentValues() {
            Validation<String, String> validation1 = Validation.valid("test1");
            Validation<String, String> validation2 = Validation.valid("test2");
            
            assertThat(validation1).isNotEqualTo(validation2);
        }
        
        @Test
        @DisplayName("Should have equal Invalid instances with same errors")
        void shouldHaveEqualInvalidInstancesWithSameErrors() {
            Validation<String, String> validation1 = Validation.invalid("error1", "error2");
            Validation<String, String> validation2 = Validation.invalid("error1", "error2");
            
            assertThat(validation1).isEqualTo(validation2);
            assertThat(validation1.hashCode()).isEqualTo(validation2.hashCode());
        }
        
        @Test
        @DisplayName("Should have unequal Invalid instances with different errors")
        void shouldHaveUnequalInvalidInstancesWithDifferentErrors() {
            Validation<String, String> validation1 = Validation.invalid("error1");
            Validation<String, String> validation2 = Validation.invalid("error2");
            
            assertThat(validation1).isNotEqualTo(validation2);
        }
        
        @Test
        @DisplayName("Should have different Valid and Invalid instances")
        void shouldHaveDifferentValidAndInvalidInstances() {
            Validation<String, String> valid = Validation.valid("test");
            Validation<String, String> invalid = Validation.invalid("error");
            
            assertThat(valid).isNotEqualTo(invalid);
        }
        
        @Test
        @DisplayName("Should have meaningful string representation for Valid")
        void shouldHaveMeaningfulStringRepresentationForValid() {
            Validation<String, String> validation = Validation.valid("test");
            
            assertThat(validation.toString()).isEqualTo("Valid(test)");
        }
        
        @Test
        @DisplayName("Should have meaningful string representation for Invalid")
        void shouldHaveMeaningfulStringRepresentationForInvalid() {
            Validation<String, String> validation = Validation.invalid("error1", "error2");
            
            assertThat(validation.toString()).isEqualTo("Invalid([error1, error2])");
        }
    }
}