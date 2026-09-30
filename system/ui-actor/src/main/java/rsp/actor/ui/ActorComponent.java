package rsp.actor.ui;

import rsp.actor.ActorDefinition;
import rsp.actor.SendResult;
import rsp.actor.runtime.ActorSnapshot;
import rsp.actor.runtime.ActorView;
import rsp.component.ComponentView;
import rsp.page.events.GenericTaskEvent;
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

/**
 * Optional component base whose authoritative state and intent processing are
 * provided by a page-owned or application-owned actor.
 * <p>
 * Bindings resolve on the first real render. A page binding lazily activates an
 * actor owned by its page scope; an existing-view binding borrows an application
 * actor. Unmounting releases rendering resources without closing either actor.
 * Committed immutable snapshots require no domain subscription messages.
 */
public abstract class ActorComponent<S, M> extends Component<S, M> {
    protected ActorComponent() {
    }

    protected ActorComponent(Object componentType) {
        super(componentType);
    }

    /** Override this for an existing application-owned actor, or use the page placement bridge. */
    protected ActorBinding<S, M> binding(ActorComponentContext context) {
        return _ -> Objects.requireNonNull(placement(context), "actor placement")
                .activate(Objects.requireNonNull(definition(), "actor definition")).view();
    }

    /** Compatibility path for page-owned components. */
    protected ActorDefinition<S, M> definition() {
        throw new UnsupportedOperationException("Override binding() or definition() and placement()");
    }

    /** Compatibility path for page-owned components. */
    protected PageActorPlacement<M> placement(ActorComponentContext context) {
        throw new UnsupportedOperationException("Override binding() or definition() and placement()");
    }

    protected ActorRenderPolicy renderPolicy() {
        return ActorRenderPolicy.immediate();
    }

    /** Override to render lifecycle metadata alongside domain state. */
    protected ComponentView<ActorSnapshot<S>, M> snapshotView() {
        return commands -> snapshot -> componentView().resolve(commands).apply(snapshot.state());
    }

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

    private ActorView<S, M> activation(ComponentCompositeKey componentId,
                                        ComponentContext componentContext,
                                        CommandsEnqueue commandsEnqueue) {
        ActorComponentContext context = new ActorComponentContext(
                componentId, componentContext, commandsEnqueue);
        return Objects.requireNonNull(Objects.requireNonNull(binding(context), "actor binding")
                .resolve(context), "actor view");
    }

    private final class ActorRuntime implements ComponentRuntime<S, M> {
        private final ComponentRuntimeContext context;
        private ActorView<S, M> activation;
        private ActorSnapshot<S> rendered;
        private boolean updateAllowed;

        private ActorRuntime(ComponentRuntimeContext context) {
            this.context = context;
        }

        private ActorView<S, M> activation() {
            if (activation == null) {
                activation = ActorComponent.this.activation(context.componentId(),
                        context.componentContext(), context.commandsEnqueue());
            }
            return activation;
        }

        @Override
        public S getState(ComponentCompositeKey key, ComponentContext componentContext) {
            rendered = activation().snapshot();
            return rendered.state();
        }

        @Override
        public ComponentView<S, M> adaptView(ComponentView<S, M> view) {
            return commands -> state -> snapshotView().resolve(commands).apply(
                    new ActorSnapshot<>(rendered.revision(), state, rendered.status()));
        }

        @Override
        public void onIntentDispatched(M message, S state, StateUpdater<S> stateUpdater) {
            Objects.requireNonNull(message, "message");
            SendResult result = activation().snapshot().active()
                    ? activation().ref().tell(message) : SendResult.STOPPED;
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
            updateAllowed = ActorComponent.this.onBeforeUpdated(newState, commandsEnqueue);
            return updateAllowed;
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
            LatestSnapshotAttachment<S> attachment = new LatestSnapshotAttachment<>(
                    rendered, renderPolicy(), task -> commandsEnqueue.offer(new GenericTaskEvent(task)),
                    snapshot -> {
                        ActorSnapshot<S> previous = rendered;
                        rendered = snapshot;
                        updateAllowed = false;
                        try {
                            segment.setState(snapshot.state());
                            if (!updateAllowed) rendered = previous;
                        } catch (RuntimeException | Error failure) {
                            rendered = previous;
                            throw failure;
                        }
                    });
            // Own before subscribing: subscription immediately calls back, and mounting can fail.
            segment.own(attachment);
            AutoCloseable observation = activation().observeSnapshots(attachment);
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

}
