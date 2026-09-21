package rsp.stream;

import java.util.concurrent.Flow;

/**
 * Transport-neutral, demand-aware record source. A connector must honor
 * {@link Flow.Subscription#request(long)} and must not emit null deliveries.
 * Subscription cancellation stops this consumer; connector process lifecycle
 * remains the application's responsibility.
 */
public interface StreamSource<T> extends Flow.Publisher<StreamDelivery<T>> {
}
