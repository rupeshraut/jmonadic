package org.jmonadic.patterns;

import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * A Try type for handling computations that may fail with exceptions.
 * 
 * Try represents a computation that may either result in an exception, or return
 * a successfully computed value. It's similar to Result/Either but specifically
 * designed for wrapping exception-throwing operations.
 */
public sealed interface Try<T> permits Try.Success, Try.Failure {
    
    /**
     * Executes the supplier and wraps the result in a Try.
     */
    static <T> Try<T> of(Supplier<T> supplier) {
        try {
            return success(supplier.get());
        } catch (Exception e) {
            return failure(e);
        }
    }
    
    /**
     * Executes the runnable and returns a Try for Void operations.
     */
    static Try<Void> run(Runnable runnable) {
        try {
            runnable.run();
            return success(null);
        } catch (Exception e) {
            return failure(e);
        }
    }
    
    /**
     * Creates a successful Try with the given value.
     */
    static <T> Try<T> success(T value) {
        return new Success<>(value);
    }
    
    /**
     * Creates a failed Try with the given exception.
     */
    static <T> Try<T> failure(Exception exception) {
        return new Failure<>(exception);
    }
    
    /**
     * Returns true if this Try is a success.
     */
    boolean isSuccess();
    
    /**
     * Returns true if this Try is a failure.
     */
    boolean isFailure();
    
    /**
     * Returns the value if successful, otherwise throws the exception.
     */
    T get() throws Exception;
    
    /**
     * Returns the value if successful, otherwise returns the default value.
     */
    T getOrElse(T defaultValue);
    
    /**
     * Returns the value if successful, otherwise computes the default from the exception.
     */
    T getOrElse(Function<Exception, T> defaultSupplier);
    
    /**
     * Returns the exception if this is a failure.
     */
    Optional<Exception> getException();
    
    /**
     * Maps the value if this is a success.
     */
    <U> Try<U> map(Function<T, U> mapper);
    
    /**
     * Flat maps the value if this is a success.
     */
    <U> Try<U> flatMap(Function<T, Try<U>> mapper);
    
    /**
     * Recovers from a failure by applying the recovery function.
     */
    Try<T> recover(Function<Exception, T> recovery);
    
    /**
     * Recovers from a failure by applying the recovery function that returns a Try.
     */
    Try<T> recoverWith(Function<Exception, Try<T>> recovery);
    
    /**
     * Transforms this Try into another type.
     */
    <U> U transform(Function<T, U> onSuccess, Function<Exception, U> onFailure);
    
    /**
     * Converts this Try to an Optional.
     */
    Optional<T> toOptional();
    
    /**
     * Converts this Try to an Either.
     */
    Either<Exception, T> toEither();
    
    /**
     * Converts this Try to a Result.
     */
    Result<T, Exception> toResult();
    
    /**
     * Success implementation of Try.
     */
    record Success<T>(T value) implements Try<T> {
        
        @Override
        public boolean isSuccess() {
            return true;
        }
        
        @Override
        public boolean isFailure() {
            return false;
        }
        
        @Override
        public T get() {
            return value;
        }
        
        @Override
        public T getOrElse(T defaultValue) {
            return value;
        }
        
        @Override
        public T getOrElse(Function<Exception, T> defaultSupplier) {
            return value;
        }
        
        @Override
        public Optional<Exception> getException() {
            return Optional.empty();
        }
        
        @Override
        public <U> Try<U> map(Function<T, U> mapper) {
            return Try.of(() -> mapper.apply(value));
        }
        
        @Override
        public <U> Try<U> flatMap(Function<T, Try<U>> mapper) {
            try {
                return mapper.apply(value);
            } catch (Exception e) {
                return failure(e);
            }
        }
        
        @Override
        public Try<T> recover(Function<Exception, T> recovery) {
            return this;
        }
        
        @Override
        public Try<T> recoverWith(Function<Exception, Try<T>> recovery) {
            return this;
        }
        
        @Override
        public <U> U transform(Function<T, U> onSuccess, Function<Exception, U> onFailure) {
            return onSuccess.apply(value);
        }
        
        @Override
        public Optional<T> toOptional() {
            return Optional.ofNullable(value);
        }
        
        @Override
        public Either<Exception, T> toEither() {
            return Either.right(value);
        }
        
        @Override
        public Result<T, Exception> toResult() {
            return Result.success(value);
        }
    }
    
    /**
     * Failure implementation of Try.
     */
    record Failure<T>(Exception exception) implements Try<T> {
        
        @Override
        public boolean isSuccess() {
            return false;
        }
        
        @Override
        public boolean isFailure() {
            return true;
        }
        
        @Override
        public T get() throws Exception {
            throw exception;
        }
        
        @Override
        public T getOrElse(T defaultValue) {
            return defaultValue;
        }
        
        @Override
        public T getOrElse(Function<Exception, T> defaultSupplier) {
            return defaultSupplier.apply(exception);
        }
        
        @Override
        public Optional<Exception> getException() {
            return Optional.of(exception);
        }
        
        @Override
        public <U> Try<U> map(Function<T, U> mapper) {
            return failure(exception);
        }
        
        @Override
        public <U> Try<U> flatMap(Function<T, Try<U>> mapper) {
            return failure(exception);
        }
        
        @Override
        public Try<T> recover(Function<Exception, T> recovery) {
            return Try.of(() -> recovery.apply(exception));
        }
        
        @Override
        public Try<T> recoverWith(Function<Exception, Try<T>> recovery) {
            try {
                return recovery.apply(exception);
            } catch (Exception e) {
                return failure(e);
            }
        }
        
        @Override
        public <U> U transform(Function<T, U> onSuccess, Function<Exception, U> onFailure) {
            return onFailure.apply(exception);
        }
        
        @Override
        public Optional<T> toOptional() {
            return Optional.empty();
        }
        
        @Override
        public Either<Exception, T> toEither() {
            return Either.left(exception);
        }
        
        @Override
        public Result<T, Exception> toResult() {
            return Result.failure(exception);
        }
    }
}
