package rsp.actor.stream;

import org.junit.jupiter.api.Test;
import rsp.application.ApplicationContext;
import rsp.actor.ActorBehavior;
import rsp.actor.ActorDefinition;
import rsp.actor.ActorDeliveryException;
import rsp.actor.ActorEffect;
import rsp.actor.ActorEnvelope;
import rsp.actor.ActorRef;
import rsp.actor.ActorSystem;
import rsp.actor.ActorType;
import rsp.actor.MessageId;
import rsp.actor.ProcessingReceipt;
import rsp.actor.SendResult;
import rsp.actor.runtime.LocalActorSystem;
import rsp.actor.testkit.ActorTestKit;
import rsp.stream.StreamDelivery;
import rsp.stream.StreamRecordId;
import rsp.stream.StreamSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ActorStreamsTests {
    private static final ActorType<String, Command> TYPE =
            ActorType.named("stream-test", Command.class, key -> key);

    private record Command(String value) { }

    @Test
    void deferredBindingStartsAfterActorsAndStopsBeforeThem() {
        ActorTestKit kit = new ActorTestKit();
        LocalActorSystem actors = LocalActorSystem.builder()
                .executor(kit.executor()).scheduler(kit.scheduler())
                .register(ActorDefinition.<Integer, Command>builder(TYPE)
                .initialState(_ -> 0)
                .behavior(ActorBehavior.sync((_, state, _) -> ActorEffect.state(state + 1)))
                .build()).build();
        ManualSource<String> source = new ManualSource<>();
        ActorStreamBinding<String, Command> binding = ActorStreams.consumer(source,
                _ -> actors.ref(TYPE, "cart-1"), delivery -> new Command(delivery.payload()));
        assertNull(source.subscriber);
        ApplicationContext context = ApplicationContext.builder()
                .service(ActorSystem.class, actors)
                .service(ActorStreamBinding.class, binding)
                .build();

        context.start();
        assertEquals(1, source.demand);
        FakeDelivery<String> delivery = new FakeDelivery<>("orders/0/8", "one");
        source.emit(delivery);
        kit.runAll();
        assertEquals(1, delivery.acknowledgments);
        context.stop();
        assertTrue(source.cancelled);
    }

    @Test
    void synchronousSubscribeFailureRollsBackApplicationStartup() {
        ActorTestKit kit = new ActorTestKit();
        LocalActorSystem actors = LocalActorSystem.builder()
                .executor(kit.executor()).scheduler(kit.scheduler())
                .register(ActorDefinition.<Integer, Command>builder(TYPE)
                        .initialState(_ -> 0)
                        .behavior(ActorBehavior.sync((_, state, _) -> ActorEffect.state(state + 1)))
                        .build()).build();
        StreamSource<String> broken = _ -> { throw new IllegalStateException("subscribe failed"); };
        ActorStreamBinding<String, Command> binding = ActorStreams.consumer(broken,
                _ -> actors.ref(TYPE, "cart-1"), delivery -> new Command(delivery.payload()));
        ApplicationContext context = ApplicationContext.builder()
                .service(ActorSystem.class, actors)
                .service(ActorStreamBinding.class, binding)
                .build();

        assertThrows(IllegalStateException.class, context::start);
        assertEquals(ApplicationContext.State.STOPPED, context.state());
        assertInstanceOf(IllegalStateException.class,
                assertThrows(CompletionException.class,
                        () -> binding.completion().toCompletableFuture().join()).getCause());
    }

    @Test
    void requestsOneAtATimeAndAcknowledgesOnlyAfterActorProcessing() {
        ActorTestKit kit = new ActorTestKit();
        List<String> processed = new ArrayList<>();
        LocalActorSystem actors = kit.register(ActorDefinition.<Integer, Command>builder(TYPE)
                .initialState(_ -> 0)
                .behavior(ActorBehavior.sync((context, state, command) -> {
                    processed.add(context.envelope().messageId().value() + ":" + command.value());
                    return ActorEffect.state(state + 1);
                })).build()).start();
        ManualSource<String> source = new ManualSource<>();
        ActorStreamBinding<String, Command> binding = ActorStreams.consume(source,
                delivery -> actors.ref(TYPE, "cart-1"),
                delivery -> new Command(delivery.payload()));

        assertEquals(1, source.demand);
        FakeDelivery<String> first = new FakeDelivery<>("orders/0/42", "one");
        source.emit(first);
        assertEquals(0, source.demand);
        assertEquals(0, first.acknowledgments);
        kit.runAll();
        assertEquals(List.of("orders/0/42:one"), processed);
        assertEquals(1, first.acknowledgments);
        assertEquals(1, source.demand);

        FakeDelivery<String> second = new FakeDelivery<>("orders/0/43", "two");
        source.emit(second);
        source.complete();
        assertFalse(binding.completion().toCompletableFuture().isDone());
        kit.runAll();
        binding.completion().toCompletableFuture().join();
        assertEquals(1, second.acknowledgments);
        assertEquals(List.of("orders/0/42:one", "orders/0/43:two"), processed);
        actors.stop();
    }

    @Test
    void rejectedAdmissionAndMappingFailureAreNegativelyAcknowledged() {
        ManualSource<String> source = new ManualSource<>();
        ActorStreamBinding<String, Command> binding = ActorStreams.consume(source,
                _ -> ref(SendResult.MAILBOX_FULL),
                delivery -> new Command(delivery.payload()));
        FakeDelivery<String> rejected = new FakeDelivery<>("orders/0/1", "one");
        source.emit(rejected);
        assertEquals(1, rejected.negativeAcknowledgments);
        assertInstanceOf(ActorDeliveryException.class, rejected.rejection);
        assertEquals(SendResult.MAILBOX_FULL,
                ((ActorDeliveryException) rejected.rejection).result());
        assertEquals(1, source.demand);
        binding.close();

        ManualSource<String> badMappingSource = new ManualSource<>();
        ActorStreamBinding<String, Command> badMapping = ActorStreams.consume(badMappingSource,
                _ -> ref(SendResult.ACCEPTED),
                _ -> { throw new IllegalArgumentException("poison"); });
        FakeDelivery<String> poison = new FakeDelivery<>("orders/0/2", "bad");
        badMappingSource.emit(poison);
        assertInstanceOf(IllegalArgumentException.class, poison.rejection);
        assertEquals(1, badMappingSource.demand);
        badMapping.close();
    }

    @Test
    void actorProcessingFailureIsNegativelyAcknowledged() {
        ActorTestKit kit = new ActorTestKit();
        LocalActorSystem actors = kit.register(ActorDefinition.<Integer, Command>builder(TYPE)
                .initialState(_ -> 0)
                .behavior(ActorBehavior.sync((_, _, _) -> {
                    throw new IllegalArgumentException("failed behavior");
                })).build()).start();
        ManualSource<String> source = new ManualSource<>();
        ActorStreamBinding<String, Command> binding = ActorStreams.consume(source,
                _ -> actors.ref(TYPE, "broken"), delivery -> new Command(delivery.payload()));
        FakeDelivery<String> delivery = new FakeDelivery<>("orders/0/3", "bad");
        source.emit(delivery);
        kit.runAll();
        assertEquals(0, delivery.acknowledgments);
        assertEquals(1, delivery.negativeAcknowledgments);
        assertInstanceOf(IllegalArgumentException.class, delivery.rejection);
        binding.close();
        actors.stop();
    }

    @Test
    void closeCancelsDemandAndLeavesUnprocessedRecordUnsettled() {
        ActorTestKit kit = new ActorTestKit();
        LocalActorSystem actors = kit.register(ActorDefinition.<Integer, Command>builder(TYPE)
                .initialState(_ -> 0)
                .behavior(ActorBehavior.sync((_, state, _) -> ActorEffect.state(state + 1)))
                .build()).start();
        ManualSource<String> source = new ManualSource<>();
        ActorStreamBinding<String, Command> binding = ActorStreams.consume(source,
                _ -> actors.ref(TYPE, "cart-1"), delivery -> new Command(delivery.payload()));
        FakeDelivery<String> delivery = new FakeDelivery<>("orders/0/4", "one");
        source.emit(delivery);
        binding.close();
        kit.runAll();

        assertTrue(source.cancelled);
        assertEquals(0, delivery.acknowledgments);
        assertEquals(0, delivery.negativeAcknowledgments);
        binding.completion().toCompletableFuture().join();
        actors.stop();
    }

    @Test
    void failedCheckpointCancelsSourceAndFailsBinding() {
        ManualSource<String> source = new ManualSource<>();
        ActorStreamBinding<String, Command> binding = ActorStreams.consume(source,
                _ -> ref(SendResult.ACCEPTED), delivery -> new Command(delivery.payload()));
        FakeDelivery<String> delivery = new FakeDelivery<>("orders/0/5", "one");
        delivery.ackStage = CompletableFuture.failedFuture(new IllegalStateException("checkpoint failed"));
        source.emit(delivery);

        assertTrue(source.cancelled);
        assertEquals(0, source.demand);
        assertInstanceOf(IllegalStateException.class,
                assertThrows(CompletionException.class,
                        () -> binding.completion().toCompletableFuture().join()).getCause());
    }

    @Test
    void demandWaitsForAsynchronousCheckpoint() {
        ManualSource<String> source = new ManualSource<>();
        ActorStreamBinding<String, Command> binding = ActorStreams.consume(source,
                _ -> ref(SendResult.ACCEPTED), delivery -> new Command(delivery.payload()));
        FakeDelivery<String> delivery = new FakeDelivery<>("orders/0/6", "one");
        CompletableFuture<Void> checkpoint = new CompletableFuture<>();
        delivery.ackStage = checkpoint;
        source.emit(delivery);

        assertEquals(1, delivery.acknowledgments);
        assertEquals(0, source.demand);
        checkpoint.complete(null);
        assertEquals(1, source.demand);
        binding.close();
    }

    @Test
    void callerEnvelopePreservesCorrelationAndSourceErrorFailsBinding() {
        ActorTestKit kit = new ActorTestKit();
        List<ActorEnvelope<Command>> seen = new ArrayList<>();
        LocalActorSystem actors = kit.register(ActorDefinition.<Integer, Command>builder(TYPE)
                .initialState(_ -> 0)
                .behavior(ActorBehavior.sync((context, state, _) -> {
                    seen.add(context.envelope());
                    return ActorEffect.state(state + 1);
                })).build()).start();
        ManualSource<String> source = new ManualSource<>();
        ActorStreamBinding<String, Command> binding = ActorStreams.consumeEnvelopes(source,
                _ -> actors.ref(TYPE, "cart-1"),
                delivery -> new ActorEnvelope<>(new MessageId(delivery.id().value()),
                        new Command(delivery.payload()), Optional.of(new MessageId("request-7")),
                        Optional.empty()));
        FakeDelivery<String> delivery = new FakeDelivery<>("orders/0/7", "one");
        source.emit(delivery);
        kit.runAll();
        assertEquals("request-7", seen.getFirst().correlationId().orElseThrow().value());
        assertEquals(1, delivery.acknowledgments);

        source.fail(new IllegalStateException("source disconnected"));
        assertTrue(source.cancelled);
        assertInstanceOf(IllegalStateException.class,
                assertThrows(CompletionException.class,
                        () -> binding.completion().toCompletableFuture().join()).getCause());
        actors.stop();
    }

    @Test
    void synchronousSourceAndActorDoNotGrowTheCallStack() {
        int count = 10_000;
        AtomicInteger acknowledgments = new AtomicInteger();
        StreamSource<Integer> source = subscriber -> subscriber.onSubscribe(new Flow.Subscription() {
            private int next;

            @Override
            public void request(long demand) {
                assertEquals(1L, demand);
                if (next == count) {
                    subscriber.onComplete();
                    return;
                }
                int value = ++next;
                subscriber.onNext(new StreamDelivery<>() {
                    @Override public StreamRecordId id() { return new StreamRecordId("sync/" + value); }
                    @Override public Integer payload() { return value; }
                    @Override public CompletionStage<Void> acknowledge() {
                        acknowledgments.incrementAndGet();
                        return CompletableFuture.completedFuture(null);
                    }
                    @Override public CompletionStage<Void> negativeAcknowledge(Throwable cause) {
                        return CompletableFuture.failedFuture(cause);
                    }
                });
            }

            @Override public void cancel() { }
        });

        ActorStreamBinding<Integer, Command> binding = ActorStreams.consume(source,
                _ -> ref(SendResult.ACCEPTED), delivery -> new Command(String.valueOf(delivery.payload())));

        binding.completion().toCompletableFuture().join();
        assertEquals(count, acknowledgments.get());
    }

    @Test
    void overproducingSourceIsCancelled() {
        AtomicInteger cancellations = new AtomicInteger();
        StreamSource<String> broken = subscriber -> subscriber.onSubscribe(new Flow.Subscription() {
            @Override
            public void request(long demand) {
                assertEquals(1L, demand);
                subscriber.onNext(new FakeDelivery<>("broken/1", "one"));
                subscriber.onNext(new FakeDelivery<>("broken/2", "two"));
            }

            @Override
            public void cancel() {
                cancellations.incrementAndGet();
            }
        });
        ActorStreamBinding<String, Command> binding = ActorStreams.consume(broken,
                _ -> ref(SendResult.ACCEPTED), delivery -> new Command(delivery.payload()));

        assertEquals(1, cancellations.get());
        assertInstanceOf(IllegalStateException.class,
                assertThrows(CompletionException.class,
                        () -> binding.completion().toCompletableFuture().join()).getCause());
    }

    private static ActorRef<Command> ref(SendResult result) {
        return new ActorRef<>() {
            @Override
            public SendResult tell(ActorEnvelope<Command> envelope) {
                return result;
            }

            @Override
            public ProcessingReceipt track(ActorEnvelope<Command> envelope) {
                return new ProcessingReceipt(result, result == SendResult.ACCEPTED
                        ? CompletableFuture.completedFuture(null)
                        : CompletableFuture.failedFuture(new ActorDeliveryException(result)));
            }
        };
    }

    private static final class ManualSource<T> implements StreamSource<T> {
        private Flow.Subscriber<? super StreamDelivery<T>> subscriber;
        private long demand;
        private boolean cancelled;

        @Override
        public void subscribe(Flow.Subscriber<? super StreamDelivery<T>> subscriber) {
            this.subscriber = subscriber;
            subscriber.onSubscribe(new Flow.Subscription() {
                @Override
                public void request(long count) {
                    assertEquals(1L, count);
                    demand += count;
                }

                @Override
                public void cancel() {
                    cancelled = true;
                }
            });
        }

        void emit(StreamDelivery<T> delivery) {
            assertFalse(cancelled);
            assertTrue(demand > 0);
            demand--;
            subscriber.onNext(delivery);
        }

        void complete() {
            subscriber.onComplete();
        }

        void fail(Throwable failure) {
            subscriber.onError(failure);
        }
    }

    private static final class FakeDelivery<T> implements StreamDelivery<T> {
        private final StreamRecordId id;
        private final T payload;
        private CompletionStage<Void> ackStage = CompletableFuture.completedFuture(null);
        private int acknowledgments;
        private int negativeAcknowledgments;
        private Throwable rejection;

        private FakeDelivery(String id, T payload) {
            this.id = new StreamRecordId(id);
            this.payload = payload;
        }

        @Override public StreamRecordId id() { return id; }
        @Override public T payload() { return payload; }
        @Override public CompletionStage<Void> acknowledge() {
            acknowledgments++;
            return ackStage;
        }
        @Override public CompletionStage<Void> negativeAcknowledge(Throwable cause) {
            negativeAcknowledgments++;
            rejection = cause;
            return CompletableFuture.completedFuture(null);
        }
    }
}
