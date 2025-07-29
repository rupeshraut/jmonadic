package org.jmonadic.patterns;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * A functional programming construct that represents a computation that can either
 * succeed with a value or fail with accumulated errors.
 * 
 * Unlike Result which short-circuits on the first error, Validation accumulates
 * all errors, making it ideal for form validation, data parsing, and other scenarios
 * where you want to collect all validation failures before processing.
 * 
 * Validation is an Applicative Functor but not a Monad, as flatMap would not
 * preserve the error accumulation semantics.
 * 
 * @param <E> The type of errors (accumulated in a List)
 * @param <T> The type of the success value
 */
public abstract class Validation<E, T> {
    
    /**
     * Creates a successful Validation containing the given value.
     * 
     * @param value The success value
     * @return Valid instance containing the value
     */
    public static <E, T> Validation<E, T> valid(T value) {
        return new Valid<>(value);
    }
    
    /**
     * Creates a failed Validation containing a single error.
     * 
     * @param error The error
     * @return Invalid instance containing the error
     */
    public static <E, T> Validation<E, T> invalid(E error) {
        Objects.requireNonNull(error, "Error cannot be null");
        return new Invalid<>(Collections.singletonList(error));
    }
    
    /**
     * Creates a failed Validation containing multiple errors.
     * 
     * @param errors The list of errors
     * @return Invalid instance containing the errors
     */
    public static <E, T> Validation<E, T> invalid(List<E> errors) {
        Objects.requireNonNull(errors, "Errors cannot be null");
        if (errors.isEmpty()) {
            throw new IllegalArgumentException("Error list cannot be empty");
        }
        return new Invalid<>(new ArrayList<>(errors));
    }
    
    /**
     * Creates a failed Validation containing multiple errors.
     * 
     * @param errors The errors
     * @return Invalid instance containing the errors
     */
    @SafeVarargs
    public static <E, T> Validation<E, T> invalid(E... errors) {
        if (errors.length == 0) {
            throw new IllegalArgumentException("Must provide at least one error");
        }
        return invalid(Arrays.asList(errors));
    }
    
    /**
     * Creates a Validation by executing a supplier that may throw an exception.
     * 
     * @param supplier The supplier to execute
     * @param errorMapper Function to convert exceptions to errors
     * @return Valid if supplier succeeds, Invalid if it throws
     */
    public static <E, T> Validation<E, T> of(Supplier<T> supplier, Function<Exception, E> errorMapper) {
        Objects.requireNonNull(supplier, "Supplier cannot be null");
        Objects.requireNonNull(errorMapper, "Error mapper cannot be null");
        try {
            return valid(supplier.get());
        } catch (Exception e) {
            return invalid(errorMapper.apply(e));
        }
    }
    
    // Abstract methods
    
    /**
     * Returns true if this Validation represents a success.
     */
    public abstract boolean isValid();
    
    /**
     * Returns true if this Validation represents a failure.
     */
    public abstract boolean isInvalid();
    
    /**
     * Returns the success value.
     * 
     * @throws UnsupportedOperationException if this is Invalid
     */
    public abstract T getValue();
    
    /**
     * Returns the list of errors.
     * 
     * @throws UnsupportedOperationException if this is Valid
     */
    public abstract List<E> getErrors();
    
    // Transformation methods
    
    /**
     * Transforms the success value using the given function.
     * If this Validation is Invalid, returns the same Invalid with accumulated errors.
     * 
     * @param mapper The transformation function
     * @return A new Validation with the transformed value or the same errors
     */
    public abstract <U> Validation<E, U> map(Function<T, U> mapper);
    
    /**
     * Transforms the errors using the given function.
     * If this Validation is Valid, returns the same Valid.
     * 
     * @param mapper The error transformation function
     * @return A new Validation with transformed errors or the same value
     */
    public abstract <F> Validation<F, T> mapError(Function<E, F> mapper);
    
    /**
     * Transforms the errors list using the given function.
     * If this Validation is Valid, returns the same Valid.
     * 
     * @param mapper The error list transformation function
     * @return A new Validation with transformed errors or the same value
     */
    public abstract <F> Validation<F, T> mapErrors(Function<List<E>, List<F>> mapper);
    
    // Applicative operations (error accumulation)
    
    /**
     * Combines this Validation with another using a combining function.
     * This accumulates errors from both Validations if they are Invalid.
     * 
     * @param other The other Validation
     * @param combiner Function to combine success values
     * @return Valid with combined value if both are Valid, Invalid with accumulated errors otherwise
     */
    public <U, R> Validation<E, R> combine(Validation<E, U> other, BiFunction<T, U, R> combiner) {
        Objects.requireNonNull(other, "Other validation cannot be null");
        Objects.requireNonNull(combiner, "Combiner cannot be null");
        
        if (this.isValid() && other.isValid()) {
            try {
                return valid(combiner.apply(this.getValue(), other.getValue()));
            } catch (Exception e) {
                // If combiner throws, we can't easily convert to E without more context
                // For now, we'll let it propagate
                throw new RuntimeException("Combiner function threw an exception", e);
            }
        } else if (this.isInvalid() && other.isInvalid()) {
            List<E> combinedErrors = new ArrayList<>(this.getErrors());
            combinedErrors.addAll(other.getErrors());
            return invalid(combinedErrors);
        } else if (this.isInvalid()) {
            return invalid(this.getErrors());
        } else {
            return invalid(other.getErrors());
        }
    }
    
    /**
     * Applies a function wrapped in a Validation to this Validation's value.
     * This is the Applicative apply operation, accumulating errors.
     * 
     * @param validationFunction Validation containing a function
     * @return Valid with applied function result or Invalid with accumulated errors
     */
    public <U> Validation<E, U> apply(Validation<E, Function<T, U>> validationFunction) {
        Objects.requireNonNull(validationFunction, "Validation function cannot be null");
        return validationFunction.combine(this, (f, t) -> f.apply(t));
    }
    
    // Validation-specific operations
    
    /**
     * Validates the contained value using a predicate.
     * If the predicate fails, adds the error to accumulated errors.
     * 
     * @param predicate The validation predicate
     * @param error The error to add if validation fails
     * @return This Validation if predicate passes or this is already Invalid,
     *         Invalid with added error otherwise
     */
    public Validation<E, T> ensure(Predicate<T> predicate, E error) {
        Objects.requireNonNull(predicate, "Predicate cannot be null");
        Objects.requireNonNull(error, "Error cannot be null");
        
        if (isInvalid()) {
            return this;
        }
        
        try {
            if (predicate.test(getValue())) {
                return this;
            } else {
                return invalid(error);
            }
        } catch (Exception e) {
            return invalid(error); // Treat predicate exception as validation failure
        }
    }
    
    /**
     * Validates the contained value using a predicate that may produce multiple errors.
     * 
     * @param validator Function that returns errors for invalid values
     * @return This Validation if validator returns empty list or this is already Invalid,
     *         Invalid with accumulated errors otherwise
     */
    public Validation<E, T> ensureWith(Function<T, List<E>> validator) {
        Objects.requireNonNull(validator, "Validator cannot be null");
        
        if (isInvalid()) {
            return this;
        }
        
        try {
            List<E> validationErrors = validator.apply(getValue());
            if (validationErrors == null || validationErrors.isEmpty()) {
                return this;
            } else {
                return invalid(validationErrors);
            }
        } catch (Exception e) {
            // If validator throws, we need a way to convert the exception to E
            // For now, we'll let it propagate
            throw new RuntimeException("Validator function threw an exception", e);
        }
    }
    
    // Terminal operations
    
    /**
     * Returns the success value or the given default.
     * 
     * @param defaultValue The value to return if this Validation is Invalid
     * @return The success value or the default
     */
    public T getOrElse(T defaultValue) {
        return isValid() ? getValue() : defaultValue;
    }
    
    /**
     * Returns the success value or the result of calling the supplier.
     * The supplier is only called if this Validation is Invalid.
     * 
     * @param supplier The supplier to call for the default value
     * @return The success value or the supplier result
     */
    public T getOrElse(Supplier<T> supplier) {
        Objects.requireNonNull(supplier, "Supplier cannot be null");
        return isValid() ? getValue() : supplier.get();
    }
    
    /**
     * Performs an action on the success value if present.
     * 
     * @param action The action to perform
     * @return This Validation for method chaining
     */
    public Validation<E, T> peek(Consumer<T> action) {
        Objects.requireNonNull(action, "Action cannot be null");
        if (isValid()) {
            try {
                action.accept(getValue());
            } catch (Exception e) {
                // Ignore exceptions in peek
            }
        }
        return this;
    }
    
    /**
     * Performs an action on the errors if this Validation is Invalid.
     * 
     * @param action The action to perform
     * @return This Validation for method chaining
     */
    public Validation<E, T> peekErrors(Consumer<List<E>> action) {
        Objects.requireNonNull(action, "Action cannot be null");
        if (isInvalid()) {
            try {
                action.accept(getErrors());
            } catch (Exception e) {
                // Ignore exceptions in peek
            }
        }
        return this;
    }
    
    /**
     * Folds this Validation into a single value using the provided functions.
     * 
     * @param onInvalid Function to handle errors
     * @param onValid Function to handle success value
     * @return The result of applying the appropriate function
     */
    public <R> R fold(Function<List<E>, R> onInvalid, Function<T, R> onValid) {
        Objects.requireNonNull(onInvalid, "Invalid handler cannot be null");
        Objects.requireNonNull(onValid, "Valid handler cannot be null");
        return isValid() ? onValid.apply(getValue()) : onInvalid.apply(getErrors());
    }
    
    // Conversion methods
    
    /**
     * Converts this Validation to a Result.
     * If this Validation has multiple errors, only the first one is used.
     * 
     * @return Result.success if Valid, Result.failure with first error if Invalid
     */
    public Result<T, E> toResult() {
        return isValid() ? Result.success(getValue()) : Result.failure(getErrors().get(0));
    }
    
    /**
     * Converts this Validation to a Result, combining errors into a single error.
     * 
     * @param errorCombiner Function to combine multiple errors into one
     * @return Result.success if Valid, Result.failure with combined error if Invalid
     */
    public Result<T, E> toResult(Function<List<E>, E> errorCombiner) {
        Objects.requireNonNull(errorCombiner, "Error combiner cannot be null");
        return isValid() ? Result.success(getValue()) : Result.failure(errorCombiner.apply(getErrors()));
    }
    
    /**
     * Converts this Validation to an Either.
     * 
     * @return Either.right if Valid, Either.left with error list if Invalid
     */
    public Either<List<E>, T> toEither() {
        return isValid() ? Either.right(getValue()) : Either.left(getErrors());
    }
    
    /**
     * Converts this Validation to an Option.
     * 
     * @return Some if Valid, None if Invalid
     */
    public Option<T> toOption() {
        return isValid() ? Option.some(getValue()) : Option.none();
    }
    
    // Static utility methods
    
    /**
     * Combines multiple Validations into a single Validation containing a list.
     * All errors from Invalid Validations are accumulated.
     * 
     * @param validations The Validations to combine
     * @return Valid with list of all values if all are Valid, Invalid with accumulated errors otherwise
     */
    @SafeVarargs
    public static <E, T> Validation<E, List<T>> sequence(Validation<E, T>... validations) {
        return sequence(Arrays.asList(validations));
    }
    
    /**
     * Combines multiple Validations into a single Validation containing a list.
     * All errors from Invalid Validations are accumulated.
     * 
     * @param validations The Validations to combine
     * @return Valid with list of all values if all are Valid, Invalid with accumulated errors otherwise
     */
    public static <E, T> Validation<E, List<T>> sequence(List<Validation<E, T>> validations) {
        Objects.requireNonNull(validations, "Validations list cannot be null");
        
        List<T> values = new ArrayList<>();
        List<E> errors = new ArrayList<>();
        
        for (Validation<E, T> validation : validations) {
            if (validation.isValid()) {
                values.add(validation.getValue());
            } else {
                errors.addAll(validation.getErrors());
            }
        }
        
        return errors.isEmpty() ? valid(values) : invalid(errors);
    }
    
    // Concrete implementations
    
    private static final class Valid<E, T> extends Validation<E, T> {
        private final T value;
        
        private Valid(T value) {
            this.value = value;
        }
        
        @Override
        public boolean isValid() {
            return true;
        }
        
        @Override
        public boolean isInvalid() {
            return false;
        }
        
        @Override
        public T getValue() {
            return value;
        }
        
        @Override
        public List<E> getErrors() {
            throw new UnsupportedOperationException("Cannot get errors from Valid");
        }
        
        @Override
        public <U> Validation<E, U> map(Function<T, U> mapper) {
            Objects.requireNonNull(mapper, "Mapper cannot be null");
            try {
                return valid(mapper.apply(value));
            } catch (Exception e) {
                throw new RuntimeException("Mapper function threw an exception", e);
            }
        }
        
        @Override
        public <F> Validation<F, T> mapError(Function<E, F> mapper) {
            return new Valid<>(value);
        }
        
        @Override
        public <F> Validation<F, T> mapErrors(Function<List<E>, List<F>> mapper) {
            return new Valid<>(value);
        }
        
        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof Valid)) return false;
            Valid<?, ?> other = (Valid<?, ?>) obj;
            return Objects.equals(value, other.value);
        }
        
        @Override
        public int hashCode() {
            return Objects.hash(value);
        }
        
        @Override
        public String toString() {
            return "Valid(" + value + ")";
        }
    }
    
    private static final class Invalid<E, T> extends Validation<E, T> {
        private final List<E> errors;
        
        private Invalid(List<E> errors) {
            this.errors = Collections.unmodifiableList(errors);
        }
        
        @Override
        public boolean isValid() {
            return false;
        }
        
        @Override
        public boolean isInvalid() {
            return true;
        }
        
        @Override
        public T getValue() {
            throw new UnsupportedOperationException("Cannot get value from Invalid");
        }
        
        @Override
        public List<E> getErrors() {
            return errors;
        }
        
        @Override
        public <U> Validation<E, U> map(Function<T, U> mapper) {
            return new Invalid<>(errors);
        }
        
        @Override
        public <F> Validation<F, T> mapError(Function<E, F> mapper) {
            Objects.requireNonNull(mapper, "Mapper cannot be null");
            List<F> mappedErrors = errors.stream()
                .map(mapper)
                .collect(Collectors.toList());
            return new Invalid<>(mappedErrors);
        }
        
        @Override
        public <F> Validation<F, T> mapErrors(Function<List<E>, List<F>> mapper) {
            Objects.requireNonNull(mapper, "Mapper cannot be null");
            return new Invalid<>(mapper.apply(errors));
        }
        
        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof Invalid)) return false;
            Invalid<?, ?> other = (Invalid<?, ?>) obj;
            return Objects.equals(errors, other.errors);
        }
        
        @Override
        public int hashCode() {
            return Objects.hash(errors);
        }
        
        @Override
        public String toString() {
            return "Invalid(" + errors + ")";
        }
    }
}