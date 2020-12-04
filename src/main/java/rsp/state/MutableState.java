package rsp.state;

import java.util.Arrays;

public final class MutableState<S> implements UseState<S> {
    private final StateListener<S>[] listeners;
    private S state;

    public MutableState(S initialState)  {
        this(initialState, new StateListener[] {});
    }

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

    public synchronized MutableState<S> addListener(StateListener<S> stateListener) {
        final StateListener[] a = Arrays.copyOf(listeners, listeners.length + 1);
        a[a.length - 1] = stateListener;
        return new MutableState<>(state, a);
    }

    public interface StateListener<S> {
        void onNewState(S newState, MutableState<S> selfObj);
    }
}
