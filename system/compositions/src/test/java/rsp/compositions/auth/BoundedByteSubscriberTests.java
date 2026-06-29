package rsp.compositions.auth;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the OAuth response-body cap ({@link OAuthPKCEProvider.BoundedByteSubscriber}),
 * driven directly without networking.
 */
class BoundedByteSubscriberTests {

    /** A no-op subscription that records whether it was cancelled. */
    private static final class RecordingSubscription implements Flow.Subscription {
        final AtomicBoolean cancelled = new AtomicBoolean();
        @Override public void request(final long n) { }
        @Override public void cancel() { cancelled.set(true); }
    }

    @Test
    void completes_with_full_body_when_under_the_cap() throws Exception {
        final OAuthPKCEProvider.BoundedByteSubscriber sub = new OAuthPKCEProvider.BoundedByteSubscriber(10);
        sub.onSubscribe(new RecordingSubscription());
        sub.onNext(List.of(ByteBuffer.wrap("abc".getBytes(StandardCharsets.UTF_8))));
        sub.onNext(List.of(ByteBuffer.wrap("def".getBytes(StandardCharsets.UTF_8))));
        sub.onComplete();

        assertEquals("abcdef", new String(sub.getBody().toCompletableFuture().get(), StandardCharsets.UTF_8));
    }

    @Test
    void aborts_and_cancels_when_the_cap_is_exceeded() {
        final OAuthPKCEProvider.BoundedByteSubscriber sub = new OAuthPKCEProvider.BoundedByteSubscriber(5);
        final RecordingSubscription subscription = new RecordingSubscription();
        sub.onSubscribe(subscription);
        sub.onNext(List.of(ByteBuffer.wrap("abc".getBytes(StandardCharsets.UTF_8))));   // total 3, ok
        sub.onNext(List.of(ByteBuffer.wrap("defgh".getBytes(StandardCharsets.UTF_8)))); // total 8 > 5

        assertTrue(subscription.cancelled.get(), "subscription should be cancelled");
        final CompletionException ex = assertThrows(CompletionException.class,
                () -> sub.getBody().toCompletableFuture().join());
        assertInstanceOf(IOException.class, ex.getCause());
    }
}
