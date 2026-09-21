package rsp.app.gameoflife;

import java.util.Objects;
import java.util.Optional;

/** Presentation projection only; the Life actor owns the authoritative board. */
public record State(Optional<LifeGame.Snapshot> snapshot, Optional<String> error) {
    public State {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(error, "error");
    }

    public static State loading() {
        return new State(Optional.empty(), Optional.empty());
    }

    public State withSnapshot(LifeGame.Snapshot value) {
        return new State(Optional.of(value), Optional.empty());
    }

    public State withError(String message) {
        return new State(snapshot, Optional.of(message));
    }
}
