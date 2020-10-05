package rsp.state;

import java.util.Arrays;

public class MutableState<S> implements UseState<S> {
    private final StateListener<S>[] listeners;
    private volatile S state;

    public MutableState(S initialState)  {
        this(initialState, new StateListener[] {});
    }

    public MutableState(S initialState, StateListener<S>... listeners)  {
        this.state = initialState;
        this.listeners = listeners;
    }

    @Override
    public S get() {
        return state;
    }

    @Override
    public void accept(S state) {
        final S oldState = this.state;
        this.state = state;
        for (StateListener<S> listener:listeners) {
            listener.onNewState(oldState, state);
        }
    }

    public MutableState<S> addListener(StateListener<S> stateListener) {
        final StateListener[] a = Arrays.copyOf(listeners, listeners.length + 1);
        a[a.length - 1] = stateListener;
        return new MutableState<>(state, a);
    }

    public interface StateListener<S> {
        void onNewState(S oldState, S newState);
    }
}
