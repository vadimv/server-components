package rsp.actor;

import java.util.Objects;
import java.util.UUID;

/** Caller-supplied or generated identity for one logical message. */
public record MessageId(String value) {
    public MessageId {
        if (Objects.requireNonNull(value, "value").isBlank()) {
            throw new IllegalArgumentException("Message ID must not be blank");
        }
    }

    public static MessageId random() {
        return new MessageId(UUID.randomUUID().toString());
    }
}
