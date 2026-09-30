package rsp.actor.runtime;

import rsp.actor.ActorDeliveryException;
import rsp.actor.SendResult;

/** Application ownership of one activation, separate from its non-owning view. */
public final class OwnedActor<S, M> implements AutoCloseable {
    private final SerializedActorActivation<S, M> activation;

    OwnedActor(SerializedActorActivation<S, M> activation) {
        this.activation = activation;
    }

    public ActorView<S, M> view() {
        return activation.view();
    }

    @Override
    public void close() {
        activation.close(new ActorDeliveryException(SendResult.STOPPED));
    }
}
