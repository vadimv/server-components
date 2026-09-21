# Stream-to-actor integration

`stream-api` contains broker-neutral contracts: a `StreamSource<T>` is a Java
`Flow.Publisher` of `StreamDelivery<T>`, and a delivery has an opaque stable
`StreamRecordId`, payload, asynchronous acknowledgment, and asynchronous
negative acknowledgment. Source demand is explicit: connectors must not emit
more records than requested. `StreamSink<T>` is an outbound port for connector
implementations; this increment does not adapt it to `ActorRef.tell`, whose
admission result could not honestly represent an asynchronous broker publish.

`actor-stream` supplies `ActorStreams.consumer(source, target, command)`. It
returns an unstarted `ActorStreamBinding` that implements
`ApplicationLifecycle`. Register services in this order: actor system, source
connector (if it has a lifecycle), then binding. The application context starts
them in that order and stops them in reverse. For an actor system already
running, `ActorStreams.consume(...)` subscribes immediately.

```java
ActorStreamBinding<OrderEvent, OrderCommand> consumer = ActorStreams.consumer(
        source,
        delivery -> actors.ref(orderActors, delivery.payload().orderId()),
        delivery -> new OrderCommand.Apply(delivery.payload()));

ApplicationContext context = ApplicationContext.builder()
        .service(ActorSystem.class, actors)
        .service(ActorStreamBinding.class, consumer)
        .build();
```

Only one delivery is requested at a time. The binding uses `ActorRef.track`,
then calls `acknowledge()` after the actor's processing receipt succeeds. It
calls `negativeAcknowledge(cause)` for mapper failures, mailbox rejection, or
actor processing failure. The connector decides what negative acknowledgment
means: delayed retry, dead-lettering, or another policy. A failed checkpoint
stops the binding. `completion()` exposes normal source completion or a
terminal failure. Closing cancels demand and leaves an in-flight record
unsettled if its settlement has not started.

The default command mapping makes an actor `MessageId` from the stable stream
record ID. `consumeEnvelopes`/`consumerEnvelopes` allow callers to add
correlation and causation IDs. IDs do not deduplicate messages: a crash after
actor processing but before stream acknowledgment may replay the same record.
If the actor writes business tables, use a database transaction and an
idempotency key there. A processing receipt also does not await downstream
actors that receive outbound effects.

No Kafka/Pulsar implementation, durable offset store, transactional outbox,
or exactly-once promise is included. `StreamSource` methods and delivery
settlement methods should return promptly and complete asynchronous work
through their stages; the binding may call them from an actor completion
thread.
