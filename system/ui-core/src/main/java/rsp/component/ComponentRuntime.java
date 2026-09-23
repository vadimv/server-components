package rsp.component;

import java.util.Objects;

/**
 * Per-segment state, intent, and lifecycle controller.
 * <p>
 * A {@link rsp.component.definitions.Component} is a reusable definition and
 * may create many runtimes. Runtime construction must remain side-effect free:
 * reconciliation can discard a candidate before its state is initialized.
 */
public interface ComponentRuntime<S, I> extends ComponentStateSupplier<S>,
                                                 ComponentIntentHandler<S, I>,
                                                 ComponentCallbacks<S> {

    /** Adapts the traditional component callbacks into a per-segment runtime. */
    static <S, I> ComponentRuntime<S, I> delegate(
            ComponentStateSupplier<S> stateSupplier,
            ComponentIntentHandler<S, I> intentHandler,
            ComponentCallbacks<S> callbacks) {
        Objects.requireNonNull(stateSupplier, "stateSupplier");
        Objects.requireNonNull(intentHandler, "intentHandler");
        Objects.requireNonNull(callbacks, "callbacks");
        return new ComponentRuntime<>() {
            @Override
            public S getState(ComponentCompositeKey key, ComponentContext context) {
                return stateSupplier.getState(key, context);
            }

            @Override
            public void onIntentDispatched(I intent, S state, StateUpdater<S> updater) {
                intentHandler.onIntentDispatched(intent, state, updater);
            }

            @Override
            public void onBeforeRendered(ComponentSegment<S> segment, S state) {
                callbacks.onBeforeRendered(segment, state);
            }

            @Override
            public boolean onBeforeUpdated(S newState, CommandsEnqueue commandsEnqueue) {
                return callbacks.onBeforeUpdated(newState, commandsEnqueue);
            }

            @Override
            public void onAfterRendered(S state, Subscriber subscriber,
                                        CommandsEnqueue commandsEnqueue,
                                        StateUpdater<S> stateUpdater) {
                callbacks.onAfterRendered(state, subscriber, commandsEnqueue, stateUpdater);
            }

            @Override
            public void onMounted(ComponentCompositeKey componentId, S state,
                                  StateUpdater<S> stateUpdater) {
                callbacks.onMounted(componentId, state, stateUpdater);
            }

            @Override
            public void onMounted(ComponentSegment<S> segment,
                                  ComponentCompositeKey componentId, S state,
                                  CommandsEnqueue commandsEnqueue,
                                  StateUpdater<S> stateUpdater) {
                callbacks.onMounted(segment, componentId, state, commandsEnqueue, stateUpdater);
            }

            @Override
            public void onUpdated(ComponentCompositeKey componentId, S oldState,
                                  S newState, StateUpdater<S> stateUpdater) {
                callbacks.onUpdated(componentId, oldState, newState, stateUpdater);
            }

            @Override
            public void onUpdated(ComponentSegment<S> segment,
                                  ComponentCompositeKey componentId, S oldState,
                                  S newState, StateUpdater<S> stateUpdater) {
                callbacks.onUpdated(segment, componentId, oldState, newState, stateUpdater);
            }

            @Override
            public void onUnmounted(ComponentCompositeKey componentId, S state) {
                callbacks.onUnmounted(componentId, state);
            }
        };
    }
}
