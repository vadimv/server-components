package rsp.state;

import java.util.function.Consumer;
import java.util.function.Supplier;

public interface UseState<S> extends Supplier<S>, Consumer<S> {

    static <S> UseState<S> useState(Supplier<S> supplier, Consumer<S> consumer) {
        return new UseState<>() {
            @Override
            public void accept(S s) {
                consumer.accept(s);
            }

            @Override
            public S get() {
                return supplier.get();
            }
        };
    }

    static <S> UseState<S> useState(Supplier<S> supplier) {
        return new UseState<>() {
            @Override
            public void accept(S s) {
                throw new IllegalStateException("Not allowed for a read-only UseState instance");
            }

            @Override
            public S get() {
                return supplier.get();
            }
        };
    }

    static UseState<Void> useState() {
        return new UseState<>() {
            @Override
            public void accept(Void s) {
                throw new IllegalStateException("Not allowed for the Void type");
            }

            @Override
            public Void get() {
                throw new IllegalStateException("Not allowed for the Void type");
            }
        };
    }
}
