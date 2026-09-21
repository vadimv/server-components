package rsp.actor;

import java.util.Objects;

/** Indicates that a message was not admitted to a mailbox. */
public final class ActorDeliveryException extends RuntimeException {
    private final SendResult result;

    public ActorDeliveryException(SendResult result) {
        super("Actor message was not admitted: " + Objects.requireNonNull(result, "result"));
        this.result = result;
    }

    public SendResult result() {
        return result;
    }
}
