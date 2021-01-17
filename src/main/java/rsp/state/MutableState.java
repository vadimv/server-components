package rsp.state;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

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

    /**
     * Reads the current snapshot atomically.
     * @return the current state
     */
    @Override
    public synchronized S get() {
        return state;
    }

    /**
     * Writes a snapshot atomically.
     * @param s the new state
     */
    @Override
    public synchronized void accept(S s) {
        this.state = s;
        for (StateListener<S> listener:listeners) {
            listener.onNewState(s, this);
        }
    }

    /**
     * Writes the result of a {@link CompletableFuture}.
     * @param completableFuture a computation resulting in a write
     */
    @Override
    public void accept(CompletableFuture<S> completableFuture) {
        completableFuture.thenAccept(s -> accept(s));
    }

    /**
     * Performs an atomic execute and write the result operation of the provided function.
     * @param function which result will be written to the state, should not contain any long-time computation
     * otherwise it will block the pages events handling
     */
    @Override
    public void accept(Function<S, S> function) {
        synchronized (this) {
            accept(function.apply(get()));
        }
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
