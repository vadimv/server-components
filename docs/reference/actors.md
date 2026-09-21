# Local Actors

The local actor runtime provides typed, in-JVM message passing. Its core remains
independent of HTTP, UI components, streams, and persistence; `http-actor` is an
optional adapter over the ordinary HTTP router.

An `ActorType<K, M>` defines a stable logical name, a message class, and a key
encoder. `ActorId<M>` identifies one keyed instance; `ActorRef<M>` exposes only
message delivery. Each active actor processes one message at a time. Different
keys can process concurrently. `ActorBehavior<S, M>` receives the current state
and returns an `ActorEffect<S>` describing the next state, immediate messages,
delayed messages, and optional stop. An effect is not applied when behavior fails.

```java
sealed interface CounterMessage {
    record Increment(int amount, ActorRef<Integer> replyTo) implements CounterMessage {}
}

ActorType<String, CounterMessage> counters =
        ActorType.named("counter", CounterMessage.class, key -> key);

ActorDefinition<Integer, CounterMessage> definition =
        ActorDefinition.<Integer, CounterMessage>builder(counters)
                .initialState(_ -> 0)
                .behavior(ActorBehavior.sync((context, state, message) ->
                        switch (message) {
                            case CounterMessage.Increment increment ->
                                    ActorEffect.<Integer>state(state + increment.amount())
                                            .reply(increment.replyTo(), state + increment.amount());
                        }))
                .mailboxCapacity(256)
                .build();

LocalActorSystem actors = LocalActorSystem.builder().register(definition).build();
actors.start();
CompletionStage<Integer> count = actors.<CounterMessage, Integer>ask(
        actors.ref(counters, "cart-1"),
        replyTo -> new CounterMessage.Increment(1, replyTo),
        Duration.ofSeconds(2));
```

`ActorSystem` implements `ApplicationLifecycle`, so register it as a service in
`ApplicationContext` before HTTP producers. The context starts services in
registration order and stops them in reverse order. With an HTTP application,
`HttpApplication.withLifecycle(applicationContext, router)` provides that
ownership to the server. The router does not implicitly start the actor system.
Stop the system after producers have stopped.
`drainAndStop()` rejects new external messages while draining already admitted
work and immediate actor-to-actor messages. It cancels delayed messages; `stop()`
also applies a bounded shutdown timeout.
Stopping one actor cancels timers scheduled by that actor.
`ActorSystemObserver` receives lifecycle, activation, rejection, processing, and
failure events. Observer failures are isolated from actor delivery. This SPI lets
applications attach metrics or tracing without making the runtime depend on UI
or transport modules.

## Delivery contract

`tell` returns a `SendResult` for admission. `ACCEPTED` means the message entered
the local mailbox, not that it ran or was persisted. `track` additionally returns
a `ProcessingReceipt` whose stage completes after the behavior and local effects
are applied. Mailbox capacity limits *waiting* messages; the one in-flight
message is separate. A full mailbox returns `MAILBOX_FULL`.

The local runtime is **at-most-once and in-memory**. It does not retry messages,
deduplicate message IDs, persist state, or guarantee outbound delivery. Immediate
outbound sends are best-effort; `ActorSystemObserver.outboundRejected` reports
rejections. A failed behavior stops that actor key and fails its pending tracked
deliveries; other keys continue. An ask timeout does not cancel its command, and
unanswered asks fail when the system stops. Avoid blocking a behavior while
waiting for another actor; send a message and handle its later reply instead.
Activated keys remain in memory (including stopped keys); passivation and
bounded actor-count policies are future runtime work, so use finite key spaces
for this first implementation.

`MessageId` and `ActorEnvelope` carry stable message identity and optional
correlation/causation IDs for future durable or remote adapters. They do **not**
make local delivery durable. `ActorEffect.sendEnvelope` and `scheduleEnvelope`
preserve explicitly supplied IDs; the simpler `send` and `schedule` methods
generate new message IDs. Messages intended for future remote delivery should
be immutable data with explicit codecs, not callbacks or JVM resource handles.
The current `ask` helper embeds a temporary in-JVM reply reference in the
command. It tracks command processing, so a behavior failure fails the ask
promptly instead of masquerading as a reply timeout. `askEnvelope` accepts a
caller-supplied `ActorEnvelope` and message ID. Both helpers are local-only; a
remote adapter will need a logical reply address
and correlation ID instead of serializing that temporary reference.

## HTTP routes

The `http-actor` module adapts the existing `HttpRouter` and `RestRouteHandler`.
It does not register routes, own the actor system, or bypass route metadata.
For example, a typed JSON route can derive a logical actor key from a path
parameter and send a command carrying a temporary reply reference:

```java
HttpRouter router = HttpRouter.builder()
        .post("/counters/{id}", ActorRouteHandler.json(
                actors, requestCodec,
                (_, route) -> actors.ref(counters, route.requiredParameter("id")),
                (_, _, body, replyTo) -> new CounterMessage.Increment(body.amount(), replyTo),
                Duration.ofSeconds(2), responseCodec))
        .build();
```

`ask` allows a custom reply-to-`HttpResponse` mapper (including a domain
`RestException` for an expected 4xx); `json` decodes the request and returns a
200 JSON reply. `askEnvelope` and `jsonEnvelope` let a route supply a stable
message ID, for example from `Idempotency-Key`. This only propagates identity:
the local runtime does **not** deduplicate requests. A database-backed command
must enforce idempotency in its own transaction if retries are possible.

| Outcome | HTTP result |
| --- | --- |
| Mailbox full, not started, or stopping | `503 actor_unavailable` |
| Ask deadline exceeded | `504 actor_timeout` |
| Domain mapper throws `RestException` | Its specified 4xx/5xx JSON error |
| Unexpected actor behavior failure or unregistered actor type | Failed stage for the server's 500 boundary |

An HTTP timeout does not cancel an admitted command. OpenAPI metadata stays on
the ordinary route definition, so actor-backed routes are documented exactly
like other routes.

## UI snapshot bridge

`ui-actor` adds `UiActorSink.latest(updater, projector)`, a mount-owned
`ActorRef<E>` for full snapshots. It enqueues projection through the component's
`StateUpdater`, keeping actor threads out of rendering. When several snapshots
arrive before the UI processes them, only the latest pending snapshot is used.
The sink is unsuitable for deltas or events that must each be observed.
Close it in `onUnmounted` before sending an unsubscribe command; queued or late
events then become no-ops. A page queue that rejects an update closes the sink
and reports `STOPPED` to the sender without failing the actor. The actor remains
authoritative, while component state is only its rendered projection.

The [Life example](../../examples/src/main/java/rsp/app/gameoflife/Life.java)
uses one fixed, shared `life-demo` actor. It exposes a public game ID and
`READY`/`RUNNING`/`PAUSED` status through ordinary HTTP routes, while the live
component receives board snapshots. `PAUSED` is a game state, not an actor
runtime stop. The actor uses an epoch on delayed ticks so a pause/reset makes
already-scheduled ticks harmless. Discovery is an explicit fixed catalog, not a
scan of runtime actor cells. Dynamic game creation still needs a lifecycle and
passivation policy because activated actor keys are retained in memory.

## Testing

The `actor-testkit` artifact supplies `ActorTestKit`, `ActorProbe`,
`ManualActorExecutor`, and `ManualActorScheduler`. A test can admit messages,
call `runAll()` to process queued work, or call `advance(Duration)` to trigger
delayed messages and ask timeouts without sleeping.

```bash
mvn -pl harness/actor-testkit -am test
```

Stream, PostgreSQL, and distributed actor adapters are not part of
these increments. Database-backed actors can use `Unit.INSTANCE` as local
state and transact directly against business tables; no generic snapshot store
is required.
