package rsp.actor;

import java.util.Objects;

/** Location-independent identity of one keyed actor. */
public record ActorId<M>(ActorType<?, M> type, String key) {
    public ActorId {
        Objects.requireNonNull(type, "type");
        if (Objects.requireNonNull(key, "key").isBlank()) {
            throw new IllegalArgumentException("Actor key must not be blank");
        }
    }

    @Override
    public String toString() {
        return type.name() + "/" + key;
    }
}
