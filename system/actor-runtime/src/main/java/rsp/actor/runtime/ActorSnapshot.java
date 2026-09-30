package rsp.actor.runtime;

import java.util.Objects;

/** Immutable committed state and lifecycle, versioned within one activation. */
public record ActorSnapshot<S>(long revision, S state, Status status) {
    public enum Status { ACTIVE, STOPPED, FAILED }

    public ActorSnapshot {
        if (revision < 0) {
            throw new IllegalArgumentException("revision must not be negative");
        }
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(status, "status");
    }

    public boolean active() {
        return status == Status.ACTIVE;
    }
}
