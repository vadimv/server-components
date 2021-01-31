package rsp.util.data;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Represents a value of one of two possible types.
 * A value is either an instance of L or R parameter type.
 * @param <L> the left type
 * @param <R> the right type
 */
public abstract class Either<L, R> {

    private Either() {}

    public static <L, R> Either<L, R> left(L value) {
        return new Left<>(Objects.requireNonNull(value));
    }

    public static <L, R> Either<L, R> right(R value) {
        return new Right<>(Objects.requireNonNull(value));
    }

    public abstract <LL,RR> Either<LL, RR> map(Function<L, LL> funLeft, Function<R, RR> funRight);

    public abstract <LL,RR> Either<LL, RR> flatMap(Function<L, Either<LL, RR>> funLeft, Function<R, Either<LL, RR>> funRight);

    public abstract void on(Consumer<L> left, Consumer<R> right);

    private static class Left<L, R> extends Either<L, R> {
        private final L value;

        public Left(L value) {
            this.value = value;
        }

        @Override
        public <LL, RR> Either<LL, RR> map(Function<L, LL> funLeft, Function<R, RR> funRight) {
            return left(funLeft.apply(value));
        }

        @Override
        public <LL, RR> Either<LL, RR> flatMap(Function<L, Either<LL, RR>> funLeft, Function<R, Either<LL, RR>> funRight) {
            return funLeft.apply(value);
        }

        @Override
        public void on(Consumer<L> left, Consumer<R> right) {
            left.accept(value);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            final Left<?, ?> left = (Left<?, ?>) o;
            return Objects.equals(value, left.value);
        }

        @Override
        public int hashCode() {
            return Objects.hash(value);
        }

        @Override
        public String toString() {
            return "Left{" +
                    "value=" + value +
                    '}';
        }
    }

    private static class Right<L, R> extends Either<L, R> {
        private final R value;

        public Right(R value) {
            this.value = value;
        }

        @Override
        public <LL, RR> Either<LL, RR> map(Function<L, LL> funLeft, Function<R, RR> funRight) {
            return right(funRight.apply(value));
        }

        @Override
        public <LL, RR> Either<LL, RR> flatMap(Function<L, Either<LL, RR>> funLeft, Function<R, Either<LL, RR>> funRight) {
            return funRight.apply(value);
        }

        @Override
        public void on(Consumer<L> left, Consumer<R> right) {
            right.accept(value);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            final Right<?, ?> right = (Right<?, ?>) o;
            return Objects.equals(value, right.value);
        }

        @Override
        public int hashCode() {
            return Objects.hash(value);
        }

        @Override
        public String toString() {
            return "Right{" +
                    "value=" + value +
                    '}';
        }
    }
}
