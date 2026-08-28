package rsp.telemetry;

import java.util.Objects;

/** A stable typed name for an operational command, deliberately separate from reads. */
public record CommandKey<C>(String id, Class<C> commandType) {
    public CommandKey {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(commandType, "commandType");
        if (id.isBlank()) {
            throw new IllegalArgumentException("Command id must not be blank");
        }
    }
}
