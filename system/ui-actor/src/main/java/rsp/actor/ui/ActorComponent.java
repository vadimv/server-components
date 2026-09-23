package rsp.actor.ui;

import rsp.actor.ActorDefinition;
import rsp.actor.SendResult;
import rsp.component.CommandsEnqueue;
import rsp.component.ComponentCompositeKey;
import rsp.component.ComponentContext;
import rsp.component.ComponentRuntime;
import rsp.component.ComponentRuntimeContext;
import rsp.component.ComponentSegment;
import rsp.component.ComponentStateSupplier;
import rsp.component.StateUpdater;
import rsp.component.Subscriber;
import rsp.component.definitions.Component;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Optional component base whose authoritative state and intent processing are
 * provided by a page-hosted actor.
 * <p>
 * The actor is activated lazily for the first render, survives component
 * unmounts for the lifetime of the page, and exposes committed immutable state
 * to the mounted segment without domain subscription messages.
 */
public abstract class ActorComponent<S, M> extends Component<S, M> {
    protected ActorComponent() {
    }

    protected ActorComponent(Object componentType) {
        super(componentType);
    }

    protected abstract ActorDefinition<S, M> definition();

    protected abstract PageActorPlacement<M> placement(ActorComponentContext context);

    /** Called when a view-dispatched message cannot enter the actor mailbox. */
    protected void onMessageRejected(M message, SendResult result) {
        System.getLogger(getClass().getName()).log(System.Logger.Level.WARNING,
                "Actor component message rejected [result=" + result + "]");
    }

    /**
     * Actor components are initialized by their per-segment runtime. This
     * supplier remains functional for framework integrations that resolve a
     * component definition directly.
     */
    @Override
    public final ComponentStateSupplier<S> initStateSupplier() {
        return (componentId, componentContext) -> activation(
                componentId, componentContext,
                componentContext.getRequired(CommandsEnqueue.class)).state();
    }

    @Override
    protected final ComponentRuntime<S, M> createComponentRuntime(
            ComponentRuntimeContext context) {
        Objects.requireNonNull(context, "context");
        return new ActorRuntime(context);
    }

    private PageActorHandle<S, M> activation(ComponentCompositeKey componentId,
                                              ComponentContext componentContext,
                                              CommandsEnqueue commandsEnqueue) {
        ActorComponentContext context = new ActorComponentContext(
                componentId, componentContext, commandsEnqueue);
        PageActorPlacement<M> selected = Objects.requireNonNull(
                placement(context), "actor placement");
        return selected.activate(Objects.requireNonNull(definition(), "actor definition"));
    }

    private final class ActorRuntime implements ComponentRuntime<S, M> {
        private final ComponentRuntimeContext context;
        private PageActorHandle<S, M> activation;

        private ActorRuntime(ComponentRuntimeContext context) {
            this.context = context;
        }

        private PageActorHandle<S, M> activation() {
            if (activation == null) {
                activation = ActorComponent.this.activation(context.componentId(),
                        context.componentContext(), context.commandsEnqueue());
            }
            return activation;
        }

        @Override
        public S getState(ComponentCompositeKey key, ComponentContext componentContext) {
            return activation().state();
        }

        @Override
        public void onIntentDispatched(M message, S state, StateUpdater<S> stateUpdater) {
            Objects.requireNonNull(message, "message");
            SendResult result = activation().ref().tell(message);
            if (result != SendResult.ACCEPTED) {
                onMessageRejected(message, result);
            }
        }

        @Override
        public void onBeforeRendered(ComponentSegment<S> segment, S state) {
            ActorComponent.this.onBeforeRendered(segment, state);
        }

        @Override
        public boolean onBeforeUpdated(S newState, CommandsEnqueue commandsEnqueue) {
            return ActorComponent.this.onBeforeUpdated(newState, commandsEnqueue);
        }

        @Override
        public void onAfterRendered(S state, Subscriber subscriber,
                                    CommandsEnqueue commandsEnqueue,
                                    StateUpdater<S> stateUpdater) {
            ActorComponent.this.onAfterRendered(
                    state, subscriber, commandsEnqueue, stateUpdater);
        }

        @Override
        public void onMounted(ComponentCompositeKey componentId, S state,
                              StateUpdater<S> stateUpdater) {
            ActorComponent.this.onMounted(componentId, state, stateUpdater);
        }

        @Override
        public void onMounted(ComponentSegment<S> segment,
                              ComponentCompositeKey componentId,
                              S state,
                              CommandsEnqueue commandsEnqueue,
                              StateUpdater<S> stateUpdater) {
            LatestStateAttachment<S> attachment =
                    new LatestStateAttachment<>(stateUpdater, state);
            AutoCloseable observation = activation().observeState(attachment);
            segment.own(() -> {
                attachment.close();
                observation.close();
            });
            ActorComponent.this.onMounted(
                    segment, componentId, state, commandsEnqueue, stateUpdater);
        }

        @Override
        public void onUpdated(ComponentCompositeKey componentId, S oldState,
                              S newState, StateUpdater<S> stateUpdater) {
            ActorComponent.this.onUpdated(componentId, oldState, newState, stateUpdater);
        }

        @Override
        public void onUpdated(ComponentSegment<S> segment,
                              ComponentCompositeKey componentId,
                              S oldState,
                              S newState,
                              StateUpdater<S> stateUpdater) {
            ActorComponent.this.onUpdated(
                    segment, componentId, oldState, newState, stateUpdater);
        }

        @Override
        public void onUnmounted(ComponentCompositeKey componentId, S state) {
            ActorComponent.this.onUnmounted(componentId, state);
        }
    }

    /** Coalesces immutable snapshots while one component update is queued. */
    private static final class LatestStateAttachment<S>
            implements Consumer<S>, AutoCloseable {
        private final StateUpdater<S> updater;
        private final AtomicReference<S> latest = new AtomicReference<>();
        private final AtomicBoolean queued = new AtomicBoolean();
        private final AtomicBoolean first = new AtomicBoolean(true);
        private final AtomicBoolean closed = new AtomicBoolean();
        private final S mountedState;

        private LatestStateAttachment(StateUpdater<S> updater, S mountedState) {
            this.updater = updater;
            this.mountedState = mountedState;
        }

        @Override
        public void accept(S snapshot) {
            Objects.requireNonNull(snapshot, "actor state");
            if (first.compareAndSet(true, false) && Objects.equals(mountedState, snapshot)) {
                return;
            }
            if (closed.get()) {
                return;
            }
            latest.set(snapshot);
            enqueue();
        }

        private void enqueue() {
            if (closed.get() || !queued.compareAndSet(false, true)) {
                return;
            }
            try {
                updater.applyStateTransformation(current -> {
                    S snapshot = latest.getAndSet(null);
                    queued.set(false);
                    if (!closed.get() && latest.get() != null) {
                        enqueue();
                    }
                    return closed.get() || snapshot == null ? current : snapshot;
                });
            } catch (RuntimeException | Error failure) {
                queued.set(false);
                latest.set(null);
                throw failure;
            }
        }

        @Override
        public void close() {
            closed.set(true);
            latest.set(null);
        }
    }
}
