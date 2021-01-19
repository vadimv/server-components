package rsp.util;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

public abstract class Either<L, R> {

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
    }
}
