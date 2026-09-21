package rsp.stream;

import java.util.concurrent.CompletionStage;

/**
 * Transport-neutral outbound stream port. Completion means the connector has
 * confirmed the publish according to its own contract; it does not by itself
 * imply delivery to a consumer or exactly-once behavior.
 */
public interface StreamSink<T> {
    CompletionStage<Void> publish(StreamRecordId id, T payload);
}
