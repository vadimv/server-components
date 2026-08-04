package rsp.component.definitions;

import rsp.component.StateUpdater;

import java.util.Objects;

/**
 * Convenience base class for components whose intents are pure local-state
 * transitions. It keeps small controls such as counters concise without
 * exposing mutable state capability to their views.
 *
 * @param <S> immutable local state type
 * @param <I> component intent type
 */
public abstract class ReducerComponent<S, I> extends Component<S, I> {

    protected ReducerComponent() {
        super();
    }

    @Deprecated(forRemoval = false)
    protected ReducerComponent(final Class<I> intentType) {
        super();
    }

    @Deprecated(forRemoval = false)
    protected ReducerComponent(final Object componentType, final Class<I> intentType) {
        super(componentType);
    }

    /**
     * Produces the next state for an intent without performing effects.
     */
    protected abstract S reduce(S state, I intent);

    @Override
    protected final void onIntent(final I intent, final S state, final StateUpdater<S> stateUpdater) {
        stateUpdater.applyStateTransformation(current ->
                Objects.requireNonNull(reduce(current, intent), "Reducer cannot return null"));
    }
}
