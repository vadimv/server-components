package rsp.actor;

import java.util.Objects;
import java.util.concurrent.CompletionStage;

/**
 * Separates mailbox admission from completion of behavior and local effects.
 * Processing completion does not await downstream actors that receive emitted messages.
 */
public record ProcessingReceipt(SendResult admission, CompletionStage<Void> processed) {
    public ProcessingReceipt {
        Objects.requireNonNull(admission, "admission");
        Objects.requireNonNull(processed, "processed");
    }
}
