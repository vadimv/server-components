# Actors

The actor modules provide typed, in-JVM message passing with replaceable local
and page hosts. The core remains independent of HTTP, UI components, streams,
and persistence; adapters add those capabilities without changing domain
protocols.

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

`ActorGateway` is the host-neutral ask/reply capability used by HTTP routes.
`ActorSystem` extends it with keyed reference lookup and application lifecycle.
Register a local actor system as a service in
`ApplicationContext` before HTTP producers. The context starts services in
registration order and stops them in reverse order. With an HTTP application,
`HttpApplication.withLifecycle(applicationContext, router)` provides that
ownership to the server. The router does not implicitly start the actor system.
Stop the system after producers have stopped.
`drainAndStop()` rejects new external messages while draining already admitted
work and immediate actor-to-actor messages, including destinations first resolved
while draining. Reference lookup remains available for existing and new keys.
It cancels delayed messages; `stop()` also applies a bounded shutdown timeout.
Stopping one actor cancels timers scheduled by that actor. An actor can return
`effect.passivating()` to stop and release its local cell after accepted work
settles. Existing references remain bound to the stopped incarnation and reject
new messages; a newly obtained reference to the same key can activate a fresh
incarnation. Applications must retire external routes/references before
passivating a short-lived actor.
`ActorSystemObserver` receives lifecycle, activation, rejection, processing, and
failure events. Observer failures are isolated from actor delivery. This SPI lets
applications attach metrics or tracing without making the runtime depend on UI
or transport modules.
Local references bind a cell when obtained; actor state still initializes on
the first admitted message. Avoid obtaining references for unbounded unused
keys, and passivate short-lived actors when their owners close.

A custom local executor may run tasks inline, including `Runnable::run`.
Reentrant turns and completions drain iteratively on the executing thread, so
long self-message and actor-to-actor chains do not grow the call stack. Each task
still passes through the configured executor; queued executors retain their
scheduling behavior.

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
Ordinary message-only stopped and failed actor keys remain in memory until
passivation or runtime shutdown. Application-owned activations also release their
keys on closure or termination. Actor-count limits and durable state remain
application concerns.

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

## Page-hosted actor components

`ui-actor` supplies `PageActorRuntime`, a host that runs the shared serialized
activation engine on a page's event loop. Register it with `ApplicationContext`,
then use `PageActorDirectory` to select its page identity (numeric keys are
convenient when routes also need discovery):

```java
PageActorRuntime pageActors = PageActorRuntime.builder().build();
PageActorDirectory<Long, GameCommand> games =
        PageActorDirectory.numbered(pageActors, GAME_TYPE);

ApplicationContext application = ApplicationContext.builder()
        .service(ActorGateway.class, pageActors)
        .build();
```

An `ActorComponent<S, M>` selects a placement, renders the actor's initial state
on the first HTTP response, and sends view messages directly to its `ActorRef`:

```java
final class GameComponent extends ActorComponent<GameState, GameCommand> {
    protected ActorDefinition<GameState, GameCommand> definition() {
        return gameDefinition;
    }

    protected PageActorPlacement<GameCommand> placement(ActorComponentContext context) {
        return context.in(games);
    }

    public ComponentView<GameState, GameCommand> componentView() {
        return commands -> state -> button(
                text(Integer.toString(state.score())),
                on("click", _ -> commands.dispatch(new Play())));
    }
}
```

Committed immutable states are attached to rendering by the framework and
coalesced while a component update is queued. No `Subscribe`, `Unsubscribe`,
snapshot projection, or mount callback is required in the application protocol.
Unmounting detaches rendering but retains the activation; `PageScope` closes it
administratively when the resumable page ends. Closing rejects late messages,
cancels owned timers, and removes the exact directory entry. Timer registration
is tied to the owning activation, including when closure races with an effect
or scheduler callback. Deliveries that have already begun may finish.

Page actors initialize their state synchronously for initial rendering. After
the HTML snapshot is complete, registration starts the page event loop. Queued
behavior, asynchronous completions, and timers can progress before the first
WebSocket attachment. Actor turns and UI updates share that loop; the same
activation engine also runs standalone actors under `LocalActorSystem`.

The first WebSocket attaches to the running session and replays buffered output.
Unattached sessions have the same expiry and output bounds as disconnected
sessions: expiry or buffer overflow closes the page scope and its actors.
Browser-dependent operations, such as JavaScript evaluation, still require a
connected browser. Use `LocalActorSystem` for work whose lifetime extends beyond
a page. A page directory is not a durable store or authorization layer.

The [Life example](../../examples/src/main/java/rsp/app/gameoflife/Life.java)
uses this directory and `ActorComponent` for one actor per logical page session.
Two pages have independent boards; a WebSocket reconnect
keeps the same actor, while page closure removes the catalog entry and
administratively stops its actor. HTTP routes expose `READY`/`RUNNING`/`PAUSED` status and
controls; the live component renders committed actor state directly. The catalog
returns the games that reply within the two-second per-game deadline, omitting
unavailable games and pages closed during the request. An HTTP-only client can
list and control its game before connecting a WebSocket. Games
omitted for a timeout or full mailbox remain eligible for later listings;
individual-game routes still report availability errors and timeouts. `PAUSED`
is a game state, not an actor runtime stop. An epoch makes ticks scheduled before
pause/reset harmless. This catalog is public demo behavior, not an
authorization model for private games.

Application-owned simulations use the same activation engine with explicit
ownership and a non-owning view:

```java
OwnedActor<GameState, GameCommand> owner = actors.createOwned(GAME_TYPE.id(id), gameDefinition);
ActorView<GameState, GameCommand> view = owner.view();
ActorSnapshot<GameState> current = view.snapshot();
AutoCloseable observation = view.observeSnapshots(snapshot -> enqueueLatest(snapshot));
observation.close(); // Detach this observer only.
owner.close();       // Terminate and release this exact activation.
```

`createOwned` requires the exact registered definition, eagerly initializes state,
and rejects an occupied ID. Closing fails pending deliveries, cancels timers, and
releases the activation. An old handle or timer cannot affect a new activation at
the same ID. Every view stays bound to its original activation. Runtime shutdown
publishes terminal snapshots for retained views too.

`ActorSnapshot` includes state, revision, and ACTIVE/STOPPED/FAILED runtime status.
State must be immutable. Registration and initial snapshot capture are atomic
with respect to commits, but callbacks execute outside activation locks and can
arrive out of order. Consumers retain the highest revision for that activation.
Callbacks must be fast and nonblocking. Their exceptions do not fail the actor.
A late observer receives the final snapshot of a terminated view without being
retained. Closing an observation cannot recall a callback already executing.

The same `ActorComponent` can select either ownership mode:

```java
protected ActorBinding<GameState, GameCommand> binding(ActorComponentContext context) {
    return ActorBinding.existing(view); // Or ActorBinding.page(games, gameDefinition).
}

protected ActorRenderPolicy renderPolicy() {
    return ActorRenderPolicy.throttled(Duration.ofMillis(50), applicationRenderScheduler);
}
```

`ActorRenderScheduler` is an application lifecycle resource shared by components.
The default component policy has no minimum interval. A configured interval caps
render frequency independently of actor speed. The adapter retains one pending
snapshot and one timer or queued update. It chooses the newest revision when the
page task runs, holds its scheduling guard through rendering, and eventually
renders a final pending snapshot even if the producer stops. No idle polling or
thread per view is needed. Override `snapshotView()` to display runtime lifecycle
status; the usual `componentView()` remains sufficient for domain state alone.

Snapshot coalescing precedes DOM diffing. Generated patches retain their ordered
transport/replay semantics. Slow or disconnected viewers still have bounded
transport buffers; expiry or overflow closes their pages without closing an
application-owned simulation. Snapshot delivery skips intermediate states, so
facts that must remain visible should be retained in domain state. Snapshot
notification cost still grows with actor updates and the number of attached
viewers; this policy bounds pending rendering, not the actor's computation.

The Life example supports both modes. `/` and `/api/games` retain their page-owned
behavior. `Simulations` is a Java application service over `LocalActorSystem`;
REST calls that service, and another facade can call it without creating a page.
Its numeric ID supplier must issue unique positive IDs; the example shares one
monotonic allocator with page games. Creation reserves capacity, initializes and
starts the game, and publishes it after START's processing receipt succeeds.
The service uses an injected scheduler for the creation deadline; failed creation
releases its slot. The example limits shared games to 128 by default and renders either
mode at most once every 50 ms. The default Life tick remains 150 ms.

| Method and path | Result |
| --- | --- |
| `POST /games/shared` | Start a simulation; `201` JSON with ID, summary, URL, and a Location header |
| `GET /games/shared` | Shared simulation summaries |
| `GET /games/shared/{id}` with `Accept: text/html` | A page attached to the existing simulation |
| Same GET with `Accept: application/json` | Committed state including revision and board live-cell coordinates |
| `POST /games/shared/{id}/start`, `/pause`, `/reset` | Process a control and return its resulting summary |
| `DELETE /games/shared/{id}` | Close and remove the simulation; `204` |

Absent Accept or equal acceptable preferences select HTML on the item route.
Unsupported representations return `406`; negotiated responses include
`Vary: Accept`. JSON requests create no page sessions. Unknown IDs return `404`
without creating actors, including repeated deletion. Capacity exhaustion returns
`503`. Closing every browser leaves shared games running; deleting one leaves
its final board visible in attached pages with controls disabled.

For example, with Life running on port 8082:

```bash
curl -i -X POST http://localhost:8082/games/shared
# Open the returned /games/shared/N URL in two browsers.
curl -H 'Accept: application/json' http://localhost:8082/games/shared/N
curl -X POST http://localhost:8082/games/shared/N/pause
curl -X POST http://localhost:8082/games/shared/N/start
# Close both browsers, then reopen the same URL to see current state.
curl -X DELETE http://localhost:8082/games/shared/N
```

The equivalent Java entry point is `simulations.create()`, returning a
`CompletionStage<Simulations.Simulation>` with the ID and actor view. Register the
page runtime, local runtime, render scheduler, and simulation service under
distinct application-context keys. Stop the service before its runtimes.
Simulation lifetime is process-local; this example adds neither persistence nor
a broker connector. As with page games, the example routes are public and numeric
IDs are locators, not authorization capabilities.

## Testing

The `actor-testkit` artifact supplies `ActorTestKit`, `ActorProbe`,
`ManualActorExecutor`, and `ManualActorScheduler`. A test can admit messages,
call `runAll()` to process queued work, or call `advance(Duration)` to trigger
delayed messages and ask timeouts without sleeping.

```bash
mvn -pl harness/actor-testkit -am test
```

`actor-stream` provides broker-neutral stream ingress; it does not include a
Kafka/Pulsar connector or exactly-once delivery. See the
[stream reference](streams.md). PostgreSQL and distributed actor adapters are
not part of these increments. Database-backed actors can use `Unit.INSTANCE` as local
state and transact directly against business tables; no generic snapshot store
is required.
