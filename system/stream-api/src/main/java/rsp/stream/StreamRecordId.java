package rsp.stream;

import java.util.Objects;

/**
 * Opaque, source-namespaced identity of one logical stream record. Connectors
 * must return the same ID when redelivering that record.
 */
public record StreamRecordId(String value) {
    public StreamRecordId {
        if (Objects.requireNonNull(value, "value").isBlank()) {
            throw new IllegalArgumentException("Stream record ID must not be blank");
        }
    }
}
