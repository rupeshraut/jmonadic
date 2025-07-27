package org.jmonadic.patterns;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * A modern Result type for functional error handling without exceptions.
 * 
 * This implementation provides a type-safe way to handle operations that might fail,
 * eliminating the need for checked exceptions and enabling clean composition of
 * error-prone operations.
 * 
 * @param <T> The type of the success value
 * @param <E> The type of the error value
 */
public sealed interface Result<T, E> permits Result.Success, Result.Failure {
    
    /**
     * Creates a successful result containing the given value.
     */
    static <T, E> Result<T, E> success(T value) {
        return new Success<>(value);
    }
    
    /**
     * Creates a failed result containing the given error.
     */
    static <T, E> Result<T, E> failure(E error) {
        return new Failure<>(error);
    }
    
    /**
     * Safely executes a supplier and wraps the result or exception.
     */
    static <T> Result<T, Exception> of(Supplier<T> supplier) {
        try {
            return success(supplier.get());
        } catch (Exception e) {
            return failure(e);
        }
    }
    
    /**
     * Safely executes a runnable and returns success or failure.
     */
    static Result<Void, Exception> ofVoid(Runnable runnable) {
        try {
            runnable.run();
            return success(null);
        } catch (Exception e) {
            return failure(e);
        }
    }
    
    /**
     * Returns true if this is a successful result.
     */
    boolean isSuccess();
    
    /**
     * Returns true if this is a failed result.
     */
    boolean isFailure();
    
    /**
     * Returns the success value, or throws if this is a failure.
     */
    T getValue();
    
    /**
     * Returns the error value, or throws if this is a success.
     */
    E getError();
    
    /**
     * Returns the success value or the provided default.
     */
    T getOrElse(T defaultValue);
    
    /**
     * Returns the success value or computes it from the error.
     */
    T getOrElse(Function<E, T> mapper);
    
    /**
     * Maps the success value to a new type.
     */
    <U> Result<U, E> map(Function<T, U> mapper);
    
    /**
     * Maps the error value to a new type.
     */
    <F> Result<T, F> mapError(Function<E, F> mapper);
    
    /**
     * Flat maps the success value to a new Result.
     */
    <U> Result<U, E> flatMap(Function<T, Result<U, E>> mapper);
    
    /**
     * Filters the success value with a predicate.
     */
    Result<T, E> filter(Predicate<T> predicate, Supplier<E> errorSupplier);
    
    /**
     * Executes the appropriate consumer based on the result type.
     */
    Result<T, E> peek(Consumer<T> onSuccess, Consumer<E> onError);
    
    /**
     * Executes a consumer if this is a success.
     */
    Result<T, E> peekSuccess(Consumer<T> consumer);
    
    /**
     * Executes a consumer if this is a failure.
     */
    Result<T, E> peekError(Consumer<E> consumer);
    
    /**
     * Folds the result into a single value.
     */
    <U> U fold(Function<T, U> onSuccess, Function<E, U> onError);
    
    /**
     * Recovers from failure with a fallback result.
     */
    Result<T, E> recover(Function<E, Result<T, E>> recovery);
    
    /**
     * Recovers from failure with a fallback value.
     */
    Result<T, E> recoverWith(Function<E, T> recovery);
    
    /**
     * Success implementation of Result.
     */
    record Success<T, E>(T value) implements Result<T, E> {
        
        public Success {
            Objects.requireNonNull(value, "Success value cannot be null");
        }
        
        @Override
        public boolean isSuccess() {
            return true;
        }
        
        @Override
        public boolean isFailure() {
            return false;
        }
        
        @Override
        public T getValue() {
            return value;
        }
        
        @Override
        public E getError() {
            throw new IllegalStateException("Cannot get error from Success");
        }
        
        @Override
        public T getOrElse(T defaultValue) {
            return value;
        }
        
        @Override
        public T getOrElse(Function<E, T> mapper) {
            return value;
        }
        
        @Override
        public <U> Result<U, E> map(Function<T, U> mapper) {
            return success(mapper.apply(value));
        }
        
        @Override
        public <F> Result<T, F> mapError(Function<E, F> mapper) {
            return success(value);
        }
        
        @Override
        public <U> Result<U, E> flatMap(Function<T, Result<U, E>> mapper) {
            return mapper.apply(value);
        }
        
        @Override
        public Result<T, E> filter(Predicate<T> predicate, Supplier<E> errorSupplier) {
            return predicate.test(value) ? this : failure(errorSupplier.get());
        }
        
        @Override
        public Result<T, E> peek(Consumer<T> onSuccess, Consumer<E> onError) {
            onSuccess.accept(value);
            return this;
        }
        
        @Override
        public Result<T, E> peekSuccess(Consumer<T> consumer) {
            consumer.accept(value);
            return this;
        }
        
        @Override
        public Result<T, E> peekError(Consumer<E> consumer) {
            return this;
        }
        
        @Override
        public <U> U fold(Function<T, U> onSuccess, Function<E, U> onError) {
            return onSuccess.apply(value);
        }
        
        @Override
        public Result<T, E> recover(Function<E, Result<T, E>> recovery) {
            return this;
        }
        
        @Override
        public Result<T, E> recoverWith(Function<E, T> recovery) {
            return this;
        }
    }
    
    /**
     * Failure implementation of Result.
     */
    record Failure<T, E>(E error) implements Result<T, E> {
        
        public Failure {
            Objects.requireNonNull(error, "Failure error cannot be null");
        }
        
        @Override
        public boolean isSuccess() {
            return false;
        }
        
        @Override
        public boolean isFailure() {
            return true;
        }
        
        @Override
        public T getValue() {
            throw new IllegalStateException("Cannot get value from Failure");
        }
        
        @Override
        public E getError() {
            return error;
        }
        
        @Override
        public T getOrElse(T defaultValue) {
            return defaultValue;
        }
        
        @Override
        public T getOrElse(Function<E, T> mapper) {
            return mapper.apply(error);
        }
        
        @Override
        public <U> Result<U, E> map(Function<T, U> mapper) {
            return failure(error);
        }
        
        @Override
        public <F> Result<T, F> mapError(Function<E, F> mapper) {
            return failure(mapper.apply(error));
        }
        
        @Override
        public <U> Result<U, E> flatMap(Function<T, Result<U, E>> mapper) {
            return failure(error);
        }
        
        @Override
        public Result<T, E> filter(Predicate<T> predicate, Supplier<E> errorSupplier) {
            return this;
        }
        
        @Override
        public Result<T, E> peek(Consumer<T> onSuccess, Consumer<E> onError) {
            onError.accept(error);
            return this;
        }
        
        @Override
        public Result<T, E> peekSuccess(Consumer<T> consumer) {
            return this;
        }
        
        @Override
        public Result<T, E> peekError(Consumer<E> consumer) {
            consumer.accept(error);
            return this;
        }
        
        @Override
        public <U> U fold(Function<T, U> onSuccess, Function<E, U> onError) {
            return onError.apply(error);
        }
        
        @Override
        public Result<T, E> recover(Function<E, Result<T, E>> recovery) {
            return recovery.apply(error);
        }
        
        @Override
        public Result<T, E> recoverWith(Function<E, T> recovery) {
            return success(recovery.apply(error));
        }
    }
}
