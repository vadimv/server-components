package rsp.actor.ui;

import rsp.actor.ActorId;
import rsp.actor.ActorRef;
import rsp.actor.runtime.SerializedActorActivation;

import java.util.Objects;
import java.util.function.Consumer;

/** Host control-plane handle for one page-owned actor activation. */
public final class PageActorHandle<S, M> implements AutoCloseable {
    private final SerializedActorActivation<S, M> activation;

    PageActorHandle(SerializedActorActivation<S, M> activation) {
        this.activation = Objects.requireNonNull(activation, "activation");
    }

    public ActorId<M> id() {
        return activation.id();
    }

    public ActorRef<M> ref() {
        return activation.ref();
    }

    /** Current immutable actor state; initialized before this handle is published. */
    public S state() {
        return activation.currentState();
    }

    /** Host-level state observation, used by rendering adapters rather than domain protocols. */
    public AutoCloseable observeState(Consumer<S> observer) {
        return activation.observeState(observer);
    }

    @Override
    public void close() {
        activation.close(new PageActorClosedException());
    }

    private static final class PageActorClosedException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        private PageActorClosedException() {
            super("Page actor closed", null, false, false);
        }
    }
}
