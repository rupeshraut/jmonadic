package org.jmonadic.patterns;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * A functional programming construct representing an optional value.
 * Option encapsulates the concept of nullable values in a type-safe way,
 * eliminating null pointer exceptions and providing a fluent API for
 * working with potentially absent values.
 * 
 * This is similar to Java's Optional but designed to integrate seamlessly
 * with other monadic patterns like Result and Either.
 * 
 * @param <T> The type of the potentially present value
 */
public abstract class Option<T> {
    
    /**
     * Creates an Option containing the given value.
     * 
     * @param value The value to wrap (must not be null)
     * @return Some containing the value
     * @throws IllegalArgumentException if value is null
     */
    public static <T> Option<T> some(T value) {
        if (value == null) {
            throw new IllegalArgumentException("Value cannot be null in Some");
        }
        return new Some<>(value);
    }
    
    /**
     * Creates an empty Option.
     * 
     * @return None instance
     */
    @SuppressWarnings("unchecked")
    public static <T> Option<T> none() {
        return (Option<T>) None.INSTANCE;
    }
    
    /**
     * Creates an Option from a potentially null value.
     * 
     * @param value The value to wrap (may be null)
     * @return Some if value is not null, None otherwise
     */
    public static <T> Option<T> ofNullable(T value) {
        return value != null ? some(value) : none();
    }
    
    /**
     * Creates an Option by executing a supplier that may throw an exception.
     * 
     * @param supplier The supplier to execute
     * @return Some if supplier succeeds, None if it throws an exception
     */
    public static <T> Option<T> of(Supplier<T> supplier) {
        try {
            T value = supplier.get();
            return value != null ? some(value) : none();
        } catch (Exception e) {
            return none();
        }
    }
    
    // Abstract methods that subclasses must implement
    
    /**
     * Returns true if this Option contains a value.
     */
    public abstract boolean isSome();
    
    /**
     * Returns true if this Option is empty.
     */
    public abstract boolean isNone();
    
    /**
     * Returns the contained value.
     * 
     * @throws UnsupportedOperationException if called on None
     */
    public abstract T get();
    
    // Transformation methods
    
    /**
     * Transforms the contained value using the given function.
     * If this Option is None, returns None.
     * 
     * @param mapper The transformation function
     * @return A new Option containing the transformed value
     */
    public abstract <U> Option<U> map(Function<T, U> mapper);
    
    /**
     * Transforms the contained value using a function that returns an Option.
     * This allows chaining Option-returning operations without nesting.
     * 
     * @param mapper The transformation function that returns an Option
     * @return The resulting Option (flattened)
     */
    public abstract <U> Option<U> flatMap(Function<T, Option<U>> mapper);
    
    /**
     * Filters the contained value using a predicate.
     * If the predicate returns false or this is None, returns None.
     * 
     * @param predicate The filter condition
     * @return This Option if predicate passes, None otherwise
     */
    public abstract Option<T> filter(Predicate<T> predicate);
    
    // Terminal operations
    
    /**
     * Returns the contained value or the given default.
     * 
     * @param defaultValue The value to return if this Option is None
     * @return The contained value or the default
     */
    public abstract T orElse(T defaultValue);
    
    /**
     * Returns the contained value or the result of calling the supplier.
     * The supplier is only called if this Option is None.
     * 
     * @param supplier The supplier to call for the default value
     * @return The contained value or the supplier result
     */
    public abstract T orElseGet(Supplier<T> supplier);
    
    /**
     * Returns the contained value or throws the provided exception.
     * 
     * @param exceptionSupplier Supplier for the exception to throw
     * @return The contained value
     * @throws X if this Option is None
     */
    public abstract <X extends Throwable> T orElseThrow(Supplier<X> exceptionSupplier) throws X;
    
    /**
     * Performs an action on the contained value if present.
     * 
     * @param action The action to perform
     * @return This Option for method chaining
     */
    public abstract Option<T> peek(Consumer<T> action);
    
    /**
     * Performs an action if this Option is None.
     * 
     * @param action The action to perform
     * @return This Option for method chaining
     */
    public abstract Option<T> peekNone(Runnable action);
    
    // Conversion methods
    
    /**
     * Converts this Option to a Result.
     * 
     * @param error The error to use if this Option is None
     * @return Result.success if Some, Result.failure if None
     */
    public <E> Result<T, E> toResult(E error) {
        return isSome() ? Result.success(get()) : Result.failure(error);
    }
    
    /**
     * Converts this Option to a Result with a lazy error.
     * 
     * @param errorSupplier Supplier for the error to use if this Option is None
     * @return Result.success if Some, Result.failure if None
     */
    public <E> Result<T, E> toResult(Supplier<E> errorSupplier) {
        return isSome() ? Result.success(get()) : Result.failure(errorSupplier.get());
    }
    
    /**
     * Converts this Option to an Either.
     * 
     * @param left The left value to use if this Option is None
     * @return Either.right if Some, Either.left if None
     */
    public <L> Either<L, T> toEither(L left) {
        return isSome() ? Either.right(get()) : Either.left(left);
    }
    
    /**
     * Converts this Option to an Either with a lazy left value.
     * 
     * @param leftSupplier Supplier for the left value if this Option is None
     * @return Either.right if Some, Either.left if None
     */
    public <L> Either<L, T> toEither(Supplier<L> leftSupplier) {
        return isSome() ? Either.right(get()) : Either.left(leftSupplier.get());
    }
    
    /**
     * Converts this Option to a Stream.
     * 
     * @return A Stream containing the value if Some, empty Stream if None
     */
    public Stream<T> toStream() {
        return isSome() ? Stream.of(get()) : Stream.empty();
    }
    
    // Static utility methods
    
    /**
     * Returns the first Option that contains a value, or None if all are empty.
     * 
     * @param options The Options to check
     * @return The first Some option, or None if all are None
     */
    @SafeVarargs
    public static <T> Option<T> firstSome(Option<T>... options) {
        for (Option<T> option : options) {
            if (option.isSome()) {
                return option;
            }
        }
        return none();
    }
    
    /**
     * Combines two Options using a combining function.
     * Returns Some only if both Options contain values.
     * 
     * @param other The other Option
     * @param combiner Function to combine the values
     * @return Some with combined value if both are Some, None otherwise
     */
    public <U, R> Option<R> combine(Option<U> other, Function<T, Function<U, R>> combiner) {
        return flatMap(t -> other.map(u -> combiner.apply(t).apply(u)));
    }
    
    // Concrete implementations
    
    private static final class Some<T> extends Option<T> {
        private final T value;
        
        private Some(T value) {
            this.value = value;
        }
        
        @Override
        public boolean isSome() {
            return true;
        }
        
        @Override
        public boolean isNone() {
            return false;
        }
        
        @Override
        public T get() {
            return value;
        }
        
        @Override
        public <U> Option<U> map(Function<T, U> mapper) {
            Objects.requireNonNull(mapper, "Mapper cannot be null");
            try {
                U result = mapper.apply(value);
                return result != null ? some(result) : none();
            } catch (Exception e) {
                return none();
            }
        }
        
        @Override
        public <U> Option<U> flatMap(Function<T, Option<U>> mapper) {
            Objects.requireNonNull(mapper, "Mapper cannot be null");
            try {
                Option<U> result = mapper.apply(value);
                return result != null ? result : none();
            } catch (Exception e) {
                return none();
            }
        }
        
        @Override
        public Option<T> filter(Predicate<T> predicate) {
            Objects.requireNonNull(predicate, "Predicate cannot be null");
            try {
                return predicate.test(value) ? this : none();
            } catch (Exception e) {
                return none();
            }
        }
        
        @Override
        public T orElse(T defaultValue) {
            return value;
        }
        
        @Override
        public T orElseGet(Supplier<T> supplier) {
            return value;
        }
        
        @Override
        public <X extends Throwable> T orElseThrow(Supplier<X> exceptionSupplier) {
            return value;
        }
        
        @Override
        public Option<T> peek(Consumer<T> action) {
            Objects.requireNonNull(action, "Action cannot be null");
            try {
                action.accept(value);
            } catch (Exception e) {
                // Ignore exceptions in peek
            }
            return this;
        }
        
        @Override
        public Option<T> peekNone(Runnable action) {
            return this; // No-op for Some
        }
        
        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof Some)) return false;
            Some<?> other = (Some<?>) obj;
            return Objects.equals(value, other.value);
        }
        
        @Override
        public int hashCode() {
            return Objects.hash(value);
        }
        
        @Override
        public String toString() {
            return "Some(" + value + ")";
        }
    }
    
    private static final class None<T> extends Option<T> {
        private static final None<?> INSTANCE = new None<>();
        
        private None() {}
        
        @Override
        public boolean isSome() {
            return false;
        }
        
        @Override
        public boolean isNone() {
            return true;
        }
        
        @Override
        public T get() {
            throw new UnsupportedOperationException("Cannot get value from None");
        }
        
        @Override
        public <U> Option<U> map(Function<T, U> mapper) {
            return none();
        }
        
        @Override
        public <U> Option<U> flatMap(Function<T, Option<U>> mapper) {
            return none();
        }
        
        @Override
        public Option<T> filter(Predicate<T> predicate) {
            return this;
        }
        
        @Override
        public T orElse(T defaultValue) {
            return defaultValue;
        }
        
        @Override
        public T orElseGet(Supplier<T> supplier) {
            Objects.requireNonNull(supplier, "Supplier cannot be null");
            return supplier.get();
        }
        
        @Override
        public <X extends Throwable> T orElseThrow(Supplier<X> exceptionSupplier) throws X {
            Objects.requireNonNull(exceptionSupplier, "Exception supplier cannot be null");
            throw exceptionSupplier.get();
        }
        
        @Override
        public Option<T> peek(Consumer<T> action) {
            return this; // No-op for None
        }
        
        @Override
        public Option<T> peekNone(Runnable action) {
            Objects.requireNonNull(action, "Action cannot be null");
            try {
                action.run();
            } catch (Exception e) {
                // Ignore exceptions in peek
            }
            return this;
        }
        
        @Override
        public boolean equals(Object obj) {
            return obj instanceof None;
        }
        
        @Override
        public int hashCode() {
            return 0;
        }
        
        @Override
        public String toString() {
            return "None";
        }
    }
}