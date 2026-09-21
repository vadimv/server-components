package rsp.stream;

import java.util.concurrent.CompletionStage;

/**
 * One received record with connector-owned settlement. A consumer calls exactly
 * one settlement method at most once. Successful acknowledgment means the
 * connector accepted the checkpoint; negative acknowledgment delegates retry,
 * delay, or dead-letter policy to the connector.
 */
public interface StreamDelivery<T> {
    StreamRecordId id();

    T payload();

    CompletionStage<Void> acknowledge();

    CompletionStage<Void> negativeAcknowledge(Throwable cause);
}
