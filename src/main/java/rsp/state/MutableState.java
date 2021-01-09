package rsp.state;

import java.util.concurrent.CompletableFuture;

/**
 *  A state snapshot container with change notifications.
 * @param <S> the type of the state snapshot, an immutable class
 */
public final class MutableState<S> implements UseState<S> {
    private final StateListener<S>[] listeners;
    private S state;

    /**
     * Creates a new instance of the state snapshot container.
     * @param initialState an initial state
     */
    public MutableState(S initialState)  {
        this(initialState, new StateListener[] {});
    }

    /**
     * Creates a new instance of the state snapshot container.
     * @param initialState an initial state
     * @param listeners state change observers
     */
    @SafeVarargs
    public MutableState(S initialState, StateListener<S>... listeners)  {
        this.state = initialState;
        this.listeners = listeners;
    }

    @Override
    public synchronized S get() {
        return state;
    }

    @Override
    public synchronized void accept(S state) {
        this.state = state;
        for (StateListener<S> listener:listeners) {
            listener.onNewState(state, this);
        }
    }

    @Override
    public void accept(CompletableFuture<S> completableFuture) {
        completableFuture.thenAccept(s -> accept(s));
    }

    /**
     * The listener interface for receiving state change events.
     * @param <S> the type of the state snapshot, an immutable class
     */
    public interface StateListener<S> {

        /**
         * Invoked when a state change occurs.
         * @param newState the new state snapshot
         * @param selfObj the container self reference
         */
        void onNewState(S newState, MutableState<S> selfObj);
    }
}
