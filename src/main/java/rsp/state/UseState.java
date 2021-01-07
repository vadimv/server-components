package rsp.state;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;

public interface UseState<S> extends Supplier<S>, Consumer<S>, CompletableFutureConsumer<S> {

    static <S> UseState<S> useState(Supplier<S> supplier, Consumer<S> consumer) {
        return new UseState<>() {

            @Override
            public S get() {
                return supplier.get();
            }

            @Override
            public void accept(S s) {
                consumer.accept(s);
            }

            @Override
            public void accept(CompletableFuture<S> completableFuture) {
                completableFuture.thenAccept(s -> consumer.accept(s));
            }
        };
    }

    static <S> UseState<S> useState(Supplier<S> supplier) {
        return new UseState<>() {
            @Override
            public S get() {
                return supplier.get();
            }

            @Override
            public void accept(S s) {
                throw new IllegalStateException("Not allowed for a read-only UseState instance");
            }

            @Override
            public void accept(CompletableFuture<S> completableFuture) {
                throw new IllegalStateException("Not allowed for a read-only UseState instance");
            }
        };
    }

    static UseState<Void> useState() {
        return new UseState<>() {
            @Override
            public Void get() {
                throw new IllegalStateException("Not allowed for the Void type");
            }

            @Override
            public void accept(Void s) {
                throw new IllegalStateException("Not allowed for the Void type");
            }

            @Override
            public void accept(CompletableFuture<Void> completableFuture) {
                throw new IllegalStateException("Not allowed for the Void type");
            }
        };
    }
}
