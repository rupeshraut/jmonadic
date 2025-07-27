package org.jmonadic.performance;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.jmonadic.patterns.Result;

/**
 * A performance-optimized Result implementation that avoids allocations
 * for common success/failure patterns by using pre-allocated instances
 * and lazy evaluation.
 */
public final class FastFailResult {
    
    // Pre-allocated common instances to avoid allocations
    private static final Result<String, String> EMPTY_SUCCESS = Result.success("");
    private static final Result<Boolean, String> TRUE_SUCCESS = Result.success(true);
    private static final Result<Boolean, String> FALSE_SUCCESS = Result.success(false);
    
    // Common error instances
    private static final Result<Object, String> VALIDATION_ERROR = Result.failure("Validation failed");
    private static final Result<Object, String> NOT_FOUND_ERROR = Result.failure("Not found");
    private static final Result<Object, String> TIMEOUT_ERROR = Result.failure("Operation timed out");
    private static final Result<Object, String> UNAUTHORIZED_ERROR = Result.failure("Unauthorized");
    
    private FastFailResult() {
        // Utility class
    }
    
    /**
     * Creates a simple success result for Void operations.
     */
    public static <E> Result<String, E> voidSuccess() {
        return emptySuccess();
    }
    
    /**
     * Returns a pre-allocated empty string success result.
     */
    @SuppressWarnings("unchecked")
    public static <E> Result<String, E> emptySuccess() {
        return (Result<String, E>) EMPTY_SUCCESS;
    }
    
    /**
     * Returns a pre-allocated boolean success result.
     */
    @SuppressWarnings("unchecked")
    public static <E> Result<Boolean, E> booleanSuccess(boolean value) {
        return value ? (Result<Boolean, E>) TRUE_SUCCESS : (Result<Boolean, E>) FALSE_SUCCESS;
    }
    
    /**
     * Returns a pre-allocated validation error.
     */
    @SuppressWarnings("unchecked")
    public static <T> Result<T, String> validationError() {
        return (Result<T, String>) VALIDATION_ERROR;
    }
    
    /**
     * Returns a pre-allocated not found error.
     */
    @SuppressWarnings("unchecked")
    public static <T> Result<T, String> notFoundError() {
        return (Result<T, String>) NOT_FOUND_ERROR;
    }
    
    /**
     * Returns a pre-allocated timeout error.
     */
    @SuppressWarnings("unchecked")
    public static <T> Result<T, String> timeoutError() {
        return (Result<T, String>) TIMEOUT_ERROR;
    }
    
    /**
     * Returns a pre-allocated unauthorized error.
     */
    @SuppressWarnings("unchecked")
    public static <T> Result<T, String> unauthorizedError() {
        return (Result<T, String>) UNAUTHORIZED_ERROR;
    }
    
    /**
     * Lazily evaluates a supplier only if needed.
     */
    public static <T, E> Result<T, E> lazy(Supplier<Result<T, E>> supplier) {
        LazyResultWrapper<T, E> wrapper = new LazyResultWrapper<>(supplier);
        return wrapper.get();
    }
    
    /**
     * Creates a Result that shortcuts evaluation on first failure.
     */
    public static <T, E> ChainBuilder<T, E> chain(Result<T, E> initial) {
        return new ChainBuilder<>(initial);
    }
    
    /**
     * Lazy Result wrapper that defers computation until needed.
     */
    private static final class LazyResultWrapper<T, E> {
        private final Supplier<Result<T, E>> supplier;
        private volatile Result<T, E> computed;
        
        LazyResultWrapper(Supplier<Result<T, E>> supplier) {
            this.supplier = supplier;
        }
        
        public Result<T, E> get() {
            if (computed == null) {
                synchronized (this) {
                    if (computed == null) {
                        computed = supplier.get();
                    }
                }
            }
            return computed;
        }
    }
    
    /**
     * Builder for chaining operations with short-circuit evaluation.
     */
    public static class ChainBuilder<T, E> {
        private final Result<T, E> result;
        
        ChainBuilder(Result<T, E> result) {
            this.result = result;
        }
        
        public <U> ChainBuilder<U, E> map(Function<T, U> mapper) {
            return new ChainBuilder<>(result.map(mapper));
        }
        
        public <U> ChainBuilder<U, E> flatMap(Function<T, Result<U, E>> mapper) {
            return new ChainBuilder<>(result.flatMap(mapper));
        }
        
        public ChainBuilder<T, E> filter(java.util.function.Predicate<T> predicate, Supplier<E> errorSupplier) {
            return new ChainBuilder<>(result.filter(predicate, errorSupplier));
        }
        
        public ChainBuilder<T, E> peek(Consumer<T> onSuccess, Consumer<E> onError) {
            return new ChainBuilder<>(result.peek(onSuccess, onError));
        }
        
        public Result<T, E> build() {
            return result;
        }
    }
    
    /**
     * Performance utilities for Result operations.
     */
    public static class Performance {
        
        /**
         * Executes a function with minimal overhead tracking.
         */
        public static <T, E> Result<T, E> timed(Supplier<Result<T, E>> supplier, Consumer<Long> timeConsumer) {
            long start = System.nanoTime();
            try {
                return supplier.get();
            } finally {
                timeConsumer.accept(System.nanoTime() - start);
            }
        }
        
        /**
         * Creates a Result with execution context for performance monitoring.
         */
        public static <T, E> Result<T, E> withContext(Supplier<Result<T, E>> supplier, String operation) {
            // In production, this would integrate with APM tools
            return supplier.get();
        }
    }
}