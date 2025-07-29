package org.jmonadic.resilience.adapters;

import java.util.function.Function;
import java.util.function.Supplier;

import org.jmonadic.patterns.Result;

import io.vavr.control.Try;

/**
 * Adapter utility to bridge between JMonadic Result types and Resilience4j Try types.
 * 
 * This adapter allows seamless integration between:
 * - JMonadic functional patterns (Result<T, E>)
 * - Resilience4j resilience patterns (Try<T>)
 * 
 * Usage:
 * <pre>
 * // Convert Resilience4j Try to JMonadic Result
 * Try<String> tryValue = Try.of(() -> "success");
 * Result<String, Exception> result = Resilience4jAdapter.tryToResult(tryValue);
 * 
 * // Convert JMonadic Result to Resilience4j Try  
 * Result<String, Exception> result = Result.success("data");
 * Try<String> tryValue = Resilience4jAdapter.resultToTry(result);
 * </pre>
 */
public final class Resilience4jAdapter {

    private Resilience4jAdapter() {
        // Utility class
    }

    /**
     * Converts a Resilience4j Try<T> to a JMonadic Result<T, Exception>.
     * 
     * @param <T> the success type
     * @param tryValue the Try instance to convert
     * @return Result containing the success value or exception
     */
    public static <T> Result<T, Exception> tryToResult(Try<T> tryValue) {
        if (tryValue == null) {
            return Result.failure(new IllegalArgumentException("Try cannot be null"));
        }
        
        return tryValue.fold(
            exception -> Result.<T, Exception>failure((Exception) exception),
            success -> Result.<T, Exception>success(success)
        );
    }

    /**
     * Converts a JMonadic Result<T, Exception> to a Resilience4j Try<T>.
     * 
     * @param <T> the success type
     * @param result the Result instance to convert
     * @return Try containing the success value or exception
     */
    public static <T> Try<T> resultToTry(Result<T, Exception> result) {
        if (result == null) {
            return Try.failure(new IllegalArgumentException("Result cannot be null"));
        }
        
        return result.fold(
            success -> Try.success(success),
            exception -> Try.failure(exception)
        );
    }

    /**
     * Converts a JMonadic Result<T, E> (with any error type) to a Resilience4j Try<T>.
     * Non-Exception errors are wrapped in RuntimeException.
     * 
     * @param <T> the success type
     * @param <E> the error type
     * @param result the Result instance to convert
     * @return Try containing the success value or wrapped exception
     */
    public static <T, E> Try<T> resultToTryWithMapping(Result<T, E> result) {
        if (result == null) {
            return Try.failure(new IllegalArgumentException("Result cannot be null"));
        }
        
        return result.fold(
            success -> Try.success(success),
            error -> {
                if (error instanceof Exception) {
                    return Try.failure((Exception) error);
                } else {
                    return Try.failure(new RuntimeException("Operation failed: " + error));
                }
            }
        );
    }

    /**
     * Wraps a supplier to convert its Result return type to Try for Resilience4j usage.
     * 
     * @param <T> the success type
     * @param <E> the error type
     * @param supplier supplier returning Result<T, E>
     * @return supplier returning Try<T>
     */
    public static <T, E> Supplier<Try<T>> wrapSupplier(Supplier<Result<T, E>> supplier) {
        return () -> resultToTryWithMapping(supplier.get());
    }

    /**
     * Wraps a function to convert its Result return type to Try for Resilience4j usage.
     * 
     * @param <T> the input type
     * @param <R> the success type  
     * @param <E> the error type
     * @param function function returning Result<R, E>
     * @return function returning Try<R>
     */
    public static <T, R, E> Function<T, Try<R>> wrapFunction(Function<T, Result<R, E>> function) {
        return input -> resultToTryWithMapping(function.apply(input));
    }

    /**
     * Creates a Result-based decorator for Resilience4j patterns.
     * 
     * @param <T> the success type
     * @param resilience4jCall function that applies Resilience4j patterns and returns Try<T>
     * @return function that applies the same patterns but returns Result<T, Exception>
     */
    public static <T> Function<Supplier<T>, Result<T, Exception>> decorateSupplier(
            Function<Supplier<T>, Try<T>> resilience4jCall) {
        
        return supplier -> tryToResult(resilience4jCall.apply(supplier));
    }

    /**
     * Creates a Result-based decorator for Resilience4j patterns with custom error type.
     * 
     * @param <T> the success type
     * @param <E> the error type
     * @param resilience4jCall function that applies Resilience4j patterns and returns Try<T>
     * @param errorMapper function to map Exception to custom error type E
     * @return function that applies the same patterns but returns Result<T, E>
     */
    public static <T, E> Function<Supplier<T>, Result<T, E>> decorateSupplier(
            Function<Supplier<T>, Try<T>> resilience4jCall,
            Function<Exception, E> errorMapper) {
        
        return supplier -> {
            Try<T> tryResult = resilience4jCall.apply(supplier);
            return tryResult.fold(
                exception -> Result.<T, E>failure(errorMapper.apply((Exception) exception)),
                success -> Result.<T, E>success(success)
            );
        };
    }
}