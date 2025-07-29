package org.jmonadic.utils;

import java.util.List;
import java.util.ArrayList;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.jmonadic.patterns.Result;
import org.jmonadic.patterns.Either;

/**
 * Utility methods for common monadic operations and patterns.
 */
public final class MonadicUtils {
    
    private MonadicUtils() {
        // Utility class - prevent instantiation
    }
    
    /**
     * Combines multiple Results into a single Result containing a list.
     * If any Result is a failure, returns the first failure.
     */
    public static <T, E> Result<List<T>, E> sequence(List<Result<T, E>> results) {
        List<T> values = new ArrayList<>();
        
        for (Result<T, E> result : results) {
            if (result.isFailure()) {
                return Result.failure(result.getError());
            }
            values.add(result.getValue());
        }
        
        return Result.success(values);
    }
    
    /**
     * Traverses a list with a function that returns Results, collecting all results.
     */
    public static <T, U, E> Result<List<U>, E> traverse(List<T> items, Function<T, Result<U, E>> mapper) {
        List<Result<U, E>> results = items.stream()
            .map(mapper)
            .collect(Collectors.toList());
        return sequence(results);
    }
    
    /**
     * Filters a list of Results, keeping only successful ones.
     */
    public static <T, E> List<T> collectSuccessful(List<Result<T, E>> results) {
        return results.stream()
            .filter(Result::isSuccess)
            .map(Result::getValue)
            .collect(Collectors.toList());
    }
    
    /**
     * Filters a list of Results, keeping only failed ones.
     */
    public static <T, E> List<E> collectFailures(List<Result<T, E>> results) {
        return results.stream()
            .filter(Result::isFailure)
            .map(Result::getError)
            .collect(Collectors.toList());
    }
    
    /**
     * Retries an operation up to maxAttempts times.
     */
    public static <T> Result<T, Exception> retry(Supplier<T> operation, int maxAttempts) {
        Exception lastException = null;
        
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return Result.success(operation.get());
            } catch (Exception e) {
                lastException = e;
                if (attempt == maxAttempts) {
                    break;
                }
                
                // Simple exponential backoff
                try {
                    Thread.sleep((long) Math.pow(2, attempt - 1) * 100);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return Result.failure(ie);
                }
            }
        }
        
        return Result.failure(lastException);
    }
    
    /**
     * Retries an operation with custom delay between attempts.
     */
    public static <T> Result<T, Exception> retryWithDelay(Supplier<T> operation, int maxAttempts, long delayMs) {
        Exception lastException = null;
        
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return Result.success(operation.get());
            } catch (Exception e) {
                lastException = e;
                if (attempt == maxAttempts) {
                    break;
                }
                
                try {
                    Thread.sleep(delayMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return Result.failure(ie);
                }
            }
        }
        
        return Result.failure(lastException);
    }
    
    /**
     * Converts an Either to a Result.
     */
    public static <T, E> Result<T, E> eitherToResult(Either<E, T> either) {
        return either.isRight() 
            ? Result.success(either.getRight()) 
            : Result.failure(either.getLeft());
    }
    
    /**
     * Converts a Result to an Either.
     */
    public static <T, E> Either<E, T> resultToEither(Result<T, E> result) {
        return result.isSuccess() 
            ? Either.right(result.getValue()) 
            : Either.left(result.getError());
    }
    
    /**
     * Creates a Result from a nullable value.
     */
    public static <T> Result<T, String> fromNullable(T value, String errorMessage) {
        return value != null ? Result.success(value) : Result.failure(errorMessage);
    }
    
    /**
     * Creates a Result from a nullable value with custom error.
     */
    public static <T, E> Result<T, E> fromNullable(T value, E error) {
        return value != null ? Result.success(value) : Result.failure(error);
    }
    
    /**
     * Safely casts an object to a target type.
     */
    @SuppressWarnings("unchecked")
    public static <T> Result<T, ClassCastException> safeCast(Object obj, Class<T> targetType) {
        try {
            if (targetType.isInstance(obj)) {
                return Result.success((T) obj);
            } else {
                return Result.failure(new ClassCastException(
                    "Cannot cast " + obj.getClass().getName() + " to " + targetType.getName()));
            }
        } catch (ClassCastException e) {
            return Result.failure(e);
        }
    }
    
    /**
     * Safely parses a string to an integer.
     */
    public static Result<Integer, NumberFormatException> parseInt(String str) {
        try {
            return Result.success(Integer.parseInt(str));
        } catch (NumberFormatException e) {
            return Result.failure(e);
        }
    }
    
    /**
     * Safely parses a string to a long.
     */
    public static Result<Long, NumberFormatException> parseLong(String str) {
        try {
            return Result.success(Long.parseLong(str));
        } catch (NumberFormatException e) {
            return Result.failure(e);
        }
    }
    
    /**
     * Safely parses a string to a double.
     */
    public static Result<Double, NumberFormatException> parseDouble(String str) {
        try {
            return Result.success(Double.parseDouble(str));
        } catch (NumberFormatException e) {
            return Result.failure(e);
        }
    }
    
    /**
     * Safely accesses an array element.
     */
    public static <T> Result<T, IndexOutOfBoundsException> safeGet(T[] array, int index) {
        try {
            if (index >= 0 && index < array.length) {
                return Result.success(array[index]);
            } else {
                return Result.failure(new IndexOutOfBoundsException("Index " + index + " out of bounds for array length " + array.length));
            }
        } catch (IndexOutOfBoundsException e) {
            return Result.failure(e);
        }
    }
    
    /**
     * Safely accesses a list element.
     */
    public static <T> Result<T, IndexOutOfBoundsException> safeGet(List<T> list, int index) {
        try {
            return Result.success(list.get(index));
        } catch (IndexOutOfBoundsException e) {
            return Result.failure(e);
        }
    }
    
    /**
     * Measures execution time of an operation.
     */
    public static <T> TimedResult<T> time(Supplier<Result<T, Exception>> operation) {
        long startTime = System.nanoTime();
        Result<T, Exception> result = operation.get();
        long endTime = System.nanoTime();
        long durationNanos = endTime - startTime;
        
        return new TimedResult<>(result, durationNanos);
    }
    
    /**
     * Result with timing information.
     */
    public static class TimedResult<T> {
        private final Result<T, Exception> result;
        private final long durationNanos;
        
        public TimedResult(Result<T, Exception> result, long durationNanos) {
            this.result = result;
            this.durationNanos = durationNanos;
        }
        
        public Result<T, Exception> getResult() {
            return result;
        }
        
        public long getDurationNanos() {
            return durationNanos;
        }
        
        public double getDurationMillis() {
            return durationNanos / 1_000_000.0;
        }
        
        public double getDurationSeconds() {
            return durationNanos / 1_000_000_000.0;
        }
        
        public boolean isSuccess() {
            return result.isSuccess();
        }
        
        public boolean isFailure() {
            return result.isFailure();
        }
        
        @Override
        public String toString() {
            return "TimedResult{" +
                    "result=" + result +
                    ", durationMs=" + getDurationMillis() +
                    '}';
        }
    }
    
    /**
     * Validation utilities for common patterns.
     */
    public static class Validation {
        
        public static Result<String, String> requireNonEmpty(String value, String fieldName) {
            if (value == null || value.trim().isEmpty()) {
                return Result.failure(fieldName + " cannot be empty");
            }
            return Result.success(value.trim());
        }
        
        public static Result<String, String> requireEmail(String email) {
            return requireNonEmpty(email, "Email")
                .flatMap(e -> {
                    String emailRegex = "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$";
                    if (e.matches(emailRegex)) {
                        return Result.success(e);
                    } else {
                        return Result.failure("Email format is invalid");
                    }
                });
        }
        
        public static Result<Integer, String> requirePositive(Integer value, String fieldName) {
            if (value == null || value <= 0) {
                return Result.failure(fieldName + " must be positive");
            }
            return Result.success(value);
        }
        
        public static Result<String, String> requireLength(String value, int minLength, int maxLength, String fieldName) {
            return requireNonEmpty(value, fieldName)
                .flatMap(v -> {
                    if (v.length() < minLength || v.length() > maxLength) {
                        return Result.failure(fieldName + " must be between " + minLength + " and " + maxLength + " characters");
                    }
                    return Result.success(v);
                });
        }
    }
}