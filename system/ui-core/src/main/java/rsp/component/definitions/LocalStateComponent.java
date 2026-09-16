package rsp.component.definitions;

import rsp.component.ComponentStateSupplier;
import rsp.component.ComponentView;

import java.util.Objects;
import java.util.function.BiFunction;

/**
 * Convenience component for isolated local state whose intents reduce directly
 * into the next cache value. Its view remains intent-only.
 *
 * @param <S> immutable local state type
 * @param <I> component intent type
 */
public final class LocalStateComponent<S, I> extends ReducerComponent<S, I> {
    private final ComponentStateSupplier<S> initialState;
    private final ComponentView<S, I> view;
    private final BiFunction<S, I, S> reducer;

    public LocalStateComponent(final ComponentStateSupplier<S> initialState,
                               final ComponentView<S, I> view,
                               final BiFunction<S, I, S> reducer) {
        this.initialState = Objects.requireNonNull(initialState, "initialState");
        this.view = Objects.requireNonNull(view, "view");
        this.reducer = Objects.requireNonNull(reducer, "reducer");
    }

    @Override
    public ComponentStateSupplier<S> initStateSupplier() {
        return initialState;
    }

    @Override
    public ComponentView<S, I> componentView() {
        return view;
    }

    @Override
    protected S reduce(final S state, final I intent) {
        return reducer.apply(state, intent);
    }
}
