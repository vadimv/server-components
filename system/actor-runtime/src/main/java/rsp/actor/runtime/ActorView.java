package rsp.actor.runtime;

import rsp.actor.ActorRef;

import java.util.function.Consumer;

/** Local, non-owning access to one initialized activation. Never follows replacements. */
public interface ActorView<S, M> {
    ActorRef<M> ref();

    ActorSnapshot<S> snapshot();

    /**
     * Registers and captures the current snapshot atomically with respect to commits.
     * Callbacks run outside activation locks and may race: retain the highest revision.
     * Closing detaches this observer; an already executing callback may finish.
     * A terminal view immediately supplies its final snapshot without retaining the observer.
     * Observers must be fast and nonblocking; their exceptions are isolated from the actor.
     */
    AutoCloseable observeSnapshots(Consumer<ActorSnapshot<S>> observer);

    default S state() {
        return snapshot().state();
    }
}
