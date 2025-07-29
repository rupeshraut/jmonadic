package org.jmonadic.patterns;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Option Pattern Tests")
class OptionTest {
    
    @Nested
    @DisplayName("Construction")
    class Construction {
        
        @Test
        @DisplayName("Should create Some with non-null value")
        void shouldCreateSomeWithNonNullValue() {
            String value = "test";
            Option<String> option = Option.some(value);
            
            assertThat(option.isSome()).isTrue();
            assertThat(option.isNone()).isFalse();
            assertThat(option.get()).isEqualTo(value);
        }
        
        @Test
        @DisplayName("Should reject null value in some()")
        void shouldRejectNullValueInSome() {
            assertThatThrownBy(() -> Option.some(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Value cannot be null in Some");
        }
        
        @Test
        @DisplayName("Should create None")
        void shouldCreateNone() {
            Option<String> option = Option.none();
            
            assertThat(option.isSome()).isFalse();
            assertThat(option.isNone()).isTrue();
        }
        
        @Test
        @DisplayName("Should create Some from non-null value with ofNullable")
        void shouldCreateSomeFromNonNullValueWithOfNullable() {
            String value = "test";
            Option<String> option = Option.ofNullable(value);
            
            assertThat(option.isSome()).isTrue();
            assertThat(option.get()).isEqualTo(value);
        }
        
        @Test
        @DisplayName("Should create None from null value with ofNullable")
        void shouldCreateNoneFromNullValueWithOfNullable() {
            Option<String> option = Option.ofNullable(null);
            
            assertThat(option.isNone()).isTrue();
        }
        
        @Test
        @DisplayName("Should create Some from successful supplier")
        void shouldCreateSomeFromSuccessfulSupplier() {
            Option<String> option = Option.of(() -> "success");
            
            assertThat(option.isSome()).isTrue();
            assertThat(option.get()).isEqualTo("success");
        }
        
        @Test
        @DisplayName("Should create None from failing supplier")
        void shouldCreateNoneFromFailingSupplier() {
            Option<String> option = Option.of(() -> {
                throw new RuntimeException("failure");
            });
            
            assertThat(option.isNone()).isTrue();
        }
        
        @Test
        @DisplayName("Should create None from supplier returning null")
        void shouldCreateNoneFromSupplierReturningNull() {
            Option<String> option = Option.of(() -> null);
            
            assertThat(option.isNone()).isTrue();
        }
    }
    
    @Nested
    @DisplayName("Transformation")
    class Transformation {
        
        @Test
        @DisplayName("Should map Some value")
        void shouldMapSomeValue() {
            Option<String> option = Option.some("test");
            Option<Integer> mapped = option.map(String::length);
            
            assertThat(mapped.isSome()).isTrue();
            assertThat(mapped.get()).isEqualTo(4);
        }
        
        @Test
        @DisplayName("Should map None to None")
        void shouldMapNoneToNone() {
            Option<String> option = Option.none();
            Option<Integer> mapped = option.map(String::length);
            
            assertThat(mapped.isNone()).isTrue();
        }
        
        @Test
        @DisplayName("Should handle mapper returning null")
        void shouldHandleMapperReturningNull() {
            Option<String> option = Option.some("test");
            Option<String> mapped = option.map(s -> null);
            
            assertThat(mapped.isNone()).isTrue();
        }
        
        @Test
        @DisplayName("Should handle mapper throwing exception")
        void shouldHandleMapperThrowingException() {
            Option<String> option = Option.some("test");
            Option<String> mapped = option.map(s -> {
                throw new RuntimeException("mapper failed");
            });
            
            assertThat(mapped.isNone()).isTrue();
        }
        
        @Test
        @DisplayName("Should flatMap Some value")
        void shouldFlatMapSomeValue() {
            Option<String> option = Option.some("test");
            Option<Integer> flatMapped = option.flatMap(s -> Option.some(s.length()));
            
            assertThat(flatMapped.isSome()).isTrue();
            assertThat(flatMapped.get()).isEqualTo(4);
        }
        
        @Test
        @DisplayName("Should flatMap Some to None")
        void shouldFlatMapSomeToNone() {
            Option<String> option = Option.some("test");
            Option<Integer> flatMapped = option.flatMap(s -> Option.none());
            
            assertThat(flatMapped.isNone()).isTrue();
        }
        
        @Test
        @DisplayName("Should flatMap None to None")
        void shouldFlatMapNoneToNone() {
            Option<String> option = Option.none();
            Option<Integer> flatMapped = option.flatMap(s -> Option.some(s.length()));
            
            assertThat(flatMapped.isNone()).isTrue();
        }
        
        @Test
        @DisplayName("Should handle flatMap function throwing exception")
        void shouldHandleFlatMapFunctionThrowingException() {
            Option<String> option = Option.some("test");
            Option<String> flatMapped = option.flatMap(s -> {
                throw new RuntimeException("flatMap failed");
            });
            
            assertThat(flatMapped.isNone()).isTrue();
        }
        
        @Test
        @DisplayName("Should filter Some with passing predicate")
        void shouldFilterSomeWithPassingPredicate() {
            Option<String> option = Option.some("test");
            Option<String> filtered = option.filter(s -> s.length() > 3);
            
            assertThat(filtered.isSome()).isTrue();
            assertThat(filtered.get()).isEqualTo("test");
        }
        
        @Test
        @DisplayName("Should filter Some with failing predicate")
        void shouldFilterSomeWithFailingPredicate() {
            Option<String> option = Option.some("test");
            Option<String> filtered = option.filter(s -> s.length() > 10);
            
            assertThat(filtered.isNone()).isTrue();
        }
        
        @Test
        @DisplayName("Should filter None to None")
        void shouldFilterNoneToNone() {
            Option<String> option = Option.none();
            Option<String> filtered = option.filter(s -> true);
            
            assertThat(filtered.isNone()).isTrue();
        }
        
        @Test
        @DisplayName("Should handle filter predicate throwing exception")
        void shouldHandleFilterPredicateThrowingException() {
            Option<String> option = Option.some("test");
            Option<String> filtered = option.filter(s -> {
                throw new RuntimeException("predicate failed");
            });
            
            assertThat(filtered.isNone()).isTrue();
        }
    }
    
    @Nested
    @DisplayName("Terminal Operations")
    class TerminalOperations {
        
        @Test
        @DisplayName("Should return value with orElse for Some")
        void shouldReturnValueWithOrElseForSome() {
            Option<String> option = Option.some("test");
            String result = option.orElse("default");
            
            assertThat(result).isEqualTo("test");
        }
        
        @Test
        @DisplayName("Should return default with orElse for None")
        void shouldReturnDefaultWithOrElseForNone() {
            Option<String> option = Option.none();
            String result = option.orElse("default");
            
            assertThat(result).isEqualTo("default");
        }
        
        @Test
        @DisplayName("Should return value with orElseGet for Some")
        void shouldReturnValueWithOrElseGetForSome() {
            Option<String> option = Option.some("test");
            AtomicBoolean supplierCalled = new AtomicBoolean(false);
            
            String result = option.orElseGet(() -> {
                supplierCalled.set(true);
                return "default";
            });
            
            assertThat(result).isEqualTo("test");
            assertThat(supplierCalled.get()).isFalse();
        }
        
        @Test
        @DisplayName("Should call supplier with orElseGet for None")
        void shouldCallSupplierWithOrElseGetForNone() {
            Option<String> option = Option.none();
            AtomicBoolean supplierCalled = new AtomicBoolean(false);
            
            String result = option.orElseGet(() -> {
                supplierCalled.set(true);
                return "default";
            });
            
            assertThat(result).isEqualTo("default");
            assertThat(supplierCalled.get()).isTrue();
        }
        
        @Test
        @DisplayName("Should return value with orElseThrow for Some")
        void shouldReturnValueWithOrElseThrowForSome() {
            Option<String> option = Option.some("test");
            
            assertThatCode(() -> {
                String result = option.orElseThrow(() -> new RuntimeException("Should not throw"));
                assertThat(result).isEqualTo("test");
            }).doesNotThrowAnyException();
        }
        
        @Test
        @DisplayName("Should throw exception with orElseThrow for None")
        void shouldThrowExceptionWithOrElseThrowForNone() {
            Option<String> option = Option.none();
            
            assertThatThrownBy(() -> option.orElseThrow(() -> new RuntimeException("Value not present")))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Value not present");
        }
        
        @Test
        @DisplayName("Should throw UnsupportedOperationException when getting value from None")
        void shouldThrowUnsupportedOperationExceptionWhenGettingValueFromNone() {
            Option<String> option = Option.none();
            
            assertThatThrownBy(option::get)
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessage("Cannot get value from None");
        }
    }
    
    @Nested
    @DisplayName("Side Effects")
    class SideEffects {
        
        @Test
        @DisplayName("Should execute peek action for Some")
        void shouldExecutePeekActionForSome() {
            Option<String> option = Option.some("test");
            AtomicInteger counter = new AtomicInteger(0);
            
            Option<String> result = option.peek(s -> counter.incrementAndGet());
            
            assertThat(result).isSameAs(option);
            assertThat(counter.get()).isEqualTo(1);
        }
        
        @Test
        @DisplayName("Should not execute peek action for None")
        void shouldNotExecutePeekActionForNone() {
            Option<String> option = Option.none();
            AtomicInteger counter = new AtomicInteger(0);
            
            Option<String> result = option.peek(s -> counter.incrementAndGet());
            
            assertThat(result).isSameAs(option);
            assertThat(counter.get()).isEqualTo(0);
        }
        
        @Test
        @DisplayName("Should execute peekNone action for None")
        void shouldExecutePeekNoneActionForNone() {
            Option<String> option = Option.none();
            AtomicInteger counter = new AtomicInteger(0);
            
            Option<String> result = option.peekNone(counter::incrementAndGet);
            
            assertThat(result).isSameAs(option);
            assertThat(counter.get()).isEqualTo(1);
        }
        
        @Test
        @DisplayName("Should not execute peekNone action for Some")
        void shouldNotExecutePeekNoneActionForSome() {
            Option<String> option = Option.some("test");
            AtomicInteger counter = new AtomicInteger(0);
            
            Option<String> result = option.peekNone(counter::incrementAndGet);
            
            assertThat(result).isSameAs(option);
            assertThat(counter.get()).isEqualTo(0);
        }
    }
    
    @Nested
    @DisplayName("Conversion")
    class Conversion {
        
        @Test
        @DisplayName("Should convert Some to Result success")
        void shouldConvertSomeToResultSuccess() {
            Option<String> option = Option.some("test");
            Result<String, String> result = option.toResult("error");
            
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getValue()).isEqualTo("test");
        }
        
        @Test
        @DisplayName("Should convert None to Result failure")
        void shouldConvertNoneToResultFailure() {
            Option<String> option = Option.none();
            Result<String, String> result = option.toResult("error");
            
            assertThat(result.isFailure()).isTrue();
            assertThat(result.getError()).isEqualTo("error");
        }
        
        @Test
        @DisplayName("Should convert Some to Either right")
        void shouldConvertSomeToEitherRight() {
            Option<String> option = Option.some("test");
            Either<String, String> either = option.toEither("left");
            
            assertThat(either.isRight()).isTrue();
            assertThat(either.getRight()).isEqualTo("test");
        }
        
        @Test
        @DisplayName("Should convert None to Either left")
        void shouldConvertNoneToEitherLeft() {
            Option<String> option = Option.none();
            Either<String, String> either = option.toEither("left");
            
            assertThat(either.isLeft()).isTrue();
            assertThat(either.getLeft()).isEqualTo("left");
        }
        
        @Test
        @DisplayName("Should convert Some to non-empty Stream")
        void shouldConvertSomeToNonEmptyStream() {
            Option<String> option = Option.some("test");
            List<String> result = option.toStream().collect(Collectors.toList());
            
            assertThat(result).containsExactly("test");
        }
        
        @Test
        @DisplayName("Should convert None to empty Stream")
        void shouldConvertNoneToEmptyStream() {
            Option<String> option = Option.none();
            List<String> result = option.toStream().collect(Collectors.toList());
            
            assertThat(result).isEmpty();
        }
    }
    
    @Nested
    @DisplayName("Static Utilities")
    class StaticUtilities {
        
        @Test
        @DisplayName("Should return first Some from multiple options")
        void shouldReturnFirstSomeFromMultipleOptions() {
            Option<String> first = Option.none();
            Option<String> second = Option.some("second");
            Option<String> third = Option.some("third");
            
            Option<String> result = Option.firstSome(first, second, third);
            
            assertThat(result.isSome()).isTrue();
            assertThat(result.get()).isEqualTo("second");
        }
        
        @Test
        @DisplayName("Should return None when all options are None")
        void shouldReturnNoneWhenAllOptionsAreNone() {
            Option<String> first = Option.none();
            Option<String> second = Option.none();
            Option<String> third = Option.none();
            
            Option<String> result = Option.firstSome(first, second, third);
            
            assertThat(result.isNone()).isTrue();
        }
        
        @Test
        @DisplayName("Should combine two Some options")
        void shouldCombineTwoSomeOptions() {
            Option<String> first = Option.some("Hello");
            Option<String> second = Option.some("World");
            
            Option<String> result = first.combine(second, s1 -> s2 -> s1 + " " + s2);
            
            assertThat(result.isSome()).isTrue();
            assertThat(result.get()).isEqualTo("Hello World");
        }
        
        @Test
        @DisplayName("Should return None when combining with None")
        void shouldReturnNoneWhenCombiningWithNone() {
            Option<String> first = Option.some("Hello");
            Option<String> second = Option.none();
            
            Option<String> result = first.combine(second, s1 -> s2 -> s1 + " " + s2);
            
            assertThat(result.isNone()).isTrue();
        }
    }
    
    @Nested
    @DisplayName("Equality and String Representation")
    class EqualityAndStringRepresentation {
        
        @Test
        @DisplayName("Should have equal Some instances with same value")
        void shouldHaveEqualSomeInstancesWithSameValue() {
            Option<String> option1 = Option.some("test");
            Option<String> option2 = Option.some("test");
            
            assertThat(option1).isEqualTo(option2);
            assertThat(option1.hashCode()).isEqualTo(option2.hashCode());
        }
        
        @Test
        @DisplayName("Should have unequal Some instances with different values")
        void shouldHaveUnequalSomeInstancesWithDifferentValues() {
            Option<String> option1 = Option.some("test1");
            Option<String> option2 = Option.some("test2");
            
            assertThat(option1).isNotEqualTo(option2);
        }
        
        @Test
        @DisplayName("Should have equal None instances")
        void shouldHaveEqualNoneInstances() {
            Option<String> option1 = Option.none();
            Option<String> option2 = Option.none();
            
            assertThat(option1).isEqualTo(option2);
            assertThat(option1.hashCode()).isEqualTo(option2.hashCode());
        }
        
        @Test
        @DisplayName("Should have different Some and None instances")
        void shouldHaveDifferentSomeAndNoneInstances() {
            Option<String> some = Option.some("test");
            Option<String> none = Option.none();
            
            assertThat(some).isNotEqualTo(none);
        }
        
        @Test
        @DisplayName("Should have meaningful string representation for Some")
        void shouldHaveMeaningfulStringRepresentationForSome() {
            Option<String> option = Option.some("test");
            
            assertThat(option.toString()).isEqualTo("Some(test)");
        }
        
        @Test
        @DisplayName("Should have meaningful string representation for None")
        void shouldHaveMeaningfulStringRepresentationForNone() {
            Option<String> option = Option.none();
            
            assertThat(option.toString()).isEqualTo("None");
        }
    }
}