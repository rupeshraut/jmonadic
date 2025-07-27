package org.jmonadic.patterns;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * An Either type representing a value that can be one of two types: Left (error) or Right (success).
 * 
 * This is a fundamental algebraic data type for functional programming that enables
 * composable error handling without exceptions. By convention, Left represents
 * error cases and Right represents success cases.
 * 
 * @param <L> The type of the left (error) value
 * @param <R> The type of the right (success) value
 */
public sealed interface Either<L, R> permits Either.Left, Either.Right {
    
    /**
     * Creates a Left (error) Either containing the given value.
     */
    static <L, R> Either<L, R> left(L value) {
        return new Left<>(value);
    }
    
    /**
     * Creates a Right (success) Either containing the given value.
     */
    static <L, R> Either<L, R> right(R value) {
        return new Right<>(value);
    }
    
    /**
     * Safely executes a supplier and wraps the result or exception as Either.
     */
    static <R> Either<Exception, R> of(Supplier<R> supplier) {
        try {
            return right(supplier.get());
        } catch (Exception e) {
            return left(e);
        }
    }
    
    /**
     * Creates an Either from a nullable value.
     */
    static <L, R> Either<L, R> ofNullable(R value, Supplier<L> leftSupplier) {
        return value != null ? right(value) : left(leftSupplier.get());
    }
    
    /**
     * Returns true if this is a Left (error).
     */
    boolean isLeft();
    
    /**
     * Returns true if this is a Right (success).
     */
    boolean isRight();
    
    /**
     * Returns the left value, or throws if this is a Right.
     */
    L getLeft();
    
    /**
     * Returns the right value, or throws if this is a Left.
     */
    R getRight();
    
    /**
     * Returns the right value or the provided default.
     */
    R getOrElse(R defaultValue);
    
    /**
     * Returns the right value or computes it from the left value.
     */
    R getOrElse(Function<L, R> mapper);
    
    /**
     * Maps the right value to a new type, leaving Left unchanged.
     */
    <T> Either<L, T> map(Function<R, T> mapper);
    
    /**
     * Maps the left value to a new type, leaving Right unchanged.
     */
    <T> Either<T, R> mapLeft(Function<L, T> mapper);
    
    /**
     * Flat maps the right value to a new Either.
     */
    <T> Either<L, T> flatMap(Function<R, Either<L, T>> mapper);
    
    /**
     * Filters the right value with a predicate.
     */
    Either<L, R> filter(Predicate<R> predicate, Supplier<L> leftSupplier);
    
    /**
     * Executes the appropriate consumer based on the Either type.
     */
    Either<L, R> peek(Consumer<L> onLeft, Consumer<R> onRight);
    
    /**
     * Executes a consumer if this is a Left.
     */
    Either<L, R> peekLeft(Consumer<L> consumer);
    
    /**
     * Executes a consumer if this is a Right.
     */
    Either<L, R> peekRight(Consumer<R> consumer);
    
    /**
     * Folds the Either into a single value.
     */
    <T> T fold(Function<L, T> onLeft, Function<R, T> onRight);
    
    /**
     * Swaps Left and Right.
     */
    Either<R, L> swap();
    
    /**
     * Converts this Either to a Result.
     */
    default Result<R, L> toResult() {
        return isRight() ? Result.success(getRight()) : Result.failure(getLeft());
    }
    
    /**
     * Left implementation of Either.
     */
    record Left<L, R>(L value) implements Either<L, R> {
        
        public Left {
            Objects.requireNonNull(value, "Left value cannot be null");
        }
        
        @Override
        public boolean isLeft() {
            return true;
        }
        
        @Override
        public boolean isRight() {
            return false;
        }
        
        @Override
        public L getLeft() {
            return value;
        }
        
        @Override
        public R getRight() {
            throw new IllegalStateException("Cannot get right value from Left");
        }
        
        @Override
        public R getOrElse(R defaultValue) {
            return defaultValue;
        }
        
        @Override
        public R getOrElse(Function<L, R> mapper) {
            return mapper.apply(value);
        }
        
        @Override
        public <T> Either<L, T> map(Function<R, T> mapper) {
            return left(value);
        }
        
        @Override
        public <T> Either<T, R> mapLeft(Function<L, T> mapper) {
            return left(mapper.apply(value));
        }
        
        @Override
        public <T> Either<L, T> flatMap(Function<R, Either<L, T>> mapper) {
            return left(value);
        }
        
        @Override
        public Either<L, R> filter(Predicate<R> predicate, Supplier<L> leftSupplier) {
            return this;
        }
        
        @Override
        public Either<L, R> peek(Consumer<L> onLeft, Consumer<R> onRight) {
            onLeft.accept(value);
            return this;
        }
        
        @Override
        public Either<L, R> peekLeft(Consumer<L> consumer) {
            consumer.accept(value);
            return this;
        }
        
        @Override
        public Either<L, R> peekRight(Consumer<R> consumer) {
            return this;
        }
        
        @Override
        public <T> T fold(Function<L, T> onLeft, Function<R, T> onRight) {
            return onLeft.apply(value);
        }
        
        @Override
        public Either<R, L> swap() {
            return right(value);
        }
    }
    
    /**
     * Right implementation of Either.
     */
    record Right<L, R>(R value) implements Either<L, R> {
        
        public Right {
            Objects.requireNonNull(value, "Right value cannot be null");
        }
        
        @Override
        public boolean isLeft() {
            return false;
        }
        
        @Override
        public boolean isRight() {
            return true;
        }
        
        @Override
        public L getLeft() {
            throw new IllegalStateException("Cannot get left value from Right");
        }
        
        @Override
        public R getRight() {
            return value;
        }
        
        @Override
        public R getOrElse(R defaultValue) {
            return value;
        }
        
        @Override
        public R getOrElse(Function<L, R> mapper) {
            return value;
        }
        
        @Override
        public <T> Either<L, T> map(Function<R, T> mapper) {
            return right(mapper.apply(value));
        }
        
        @Override
        public <T> Either<T, R> mapLeft(Function<L, T> mapper) {
            return right(value);
        }
        
        @Override
        public <T> Either<L, T> flatMap(Function<R, Either<L, T>> mapper) {
            return mapper.apply(value);
        }
        
        @Override
        public Either<L, R> filter(Predicate<R> predicate, Supplier<L> leftSupplier) {
            return predicate.test(value) ? this : left(leftSupplier.get());
        }
        
        @Override
        public Either<L, R> peek(Consumer<L> onLeft, Consumer<R> onRight) {
            onRight.accept(value);
            return this;
        }
        
        @Override
        public Either<L, R> peekLeft(Consumer<L> consumer) {
            return this;
        }
        
        @Override
        public Either<L, R> peekRight(Consumer<R> consumer) {
            consumer.accept(value);
            return this;
        }
        
        @Override
        public <T> T fold(Function<L, T> onLeft, Function<R, T> onRight) {
            return onRight.apply(value);
        }
        
        @Override
        public Either<R, L> swap() {
            return left(value);
        }
    }
}
