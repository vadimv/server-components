package rsp.state;

import java.util.concurrent.CompletableFuture;

/**
 *
 * @param <S> the type of the state snapshot, an immutable class
 */
public final class ReadOnly<S> implements UseState<S> {

    private final S state;

    public ReadOnly(S state) {
        this.state = state;
    }

    @Override
    public S get() {
        return state;
    }

    @Override
    public void accept(S state) {
        throw new IllegalStateException("Set state is not allowed");
    }

    @Override
    public void accept(CompletableFuture<S> completableFuture) {
        throw new IllegalStateException("Set state is not allowed");
    }
}
