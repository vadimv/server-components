package rsp.actor;

import java.util.Objects;
import java.util.function.Function;

/** Stable logical actor type and key encoding; neither identifies a JVM or mailbox. */
public final class ActorType<K, M> {
    private final String name;
    private final Class<M> messageClass;
    private final Function<K, String> keyEncoder;

    private ActorType(String name, Class<M> messageClass, Function<K, String> keyEncoder) {
        if (Objects.requireNonNull(name, "name").isBlank()) {
            throw new IllegalArgumentException("Actor type name must not be blank");
        }
        this.name = name;
        this.messageClass = Objects.requireNonNull(messageClass, "messageClass");
        this.keyEncoder = Objects.requireNonNull(keyEncoder, "keyEncoder");
    }

    public static <K, M> ActorType<K, M> named(String name,
                                               Class<M> messageClass,
                                               Function<K, String> keyEncoder) {
        return new ActorType<>(name, messageClass, keyEncoder);
    }

    public String name() {
        return name;
    }

    public Class<M> messageClass() {
        return messageClass;
    }

    public ActorId<M> id(K key) {
        String encoded = Objects.requireNonNull(keyEncoder.apply(Objects.requireNonNull(key, "key")),
                "encoded actor key");
        if (encoded.isBlank()) {
            throw new IllegalArgumentException("Actor key must not be blank");
        }
        return new ActorId<>(this, encoded);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ActorType<?, ?> type
                && name.equals(type.name)
                && messageClass.equals(type.messageClass);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, messageClass);
    }
}
