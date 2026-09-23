# Page-hosted actor model: research and implementation plan

Status: implemented. The design was validated by migrating Game of Life; the
concrete public API is documented in the actor reference.

## Decision summary

Use one actor definition with replaceable hosts. Do not introduce a second,
parallel `ModelDefinition` abstraction in the first implementation.

- `ActorDefinition<S, M>` describes reusable logic: identity type, initial
  state, behavior, and mailbox policy.
- A host creates a live activation from that definition. The existing local
  actor system and the proposed page actor runtime are two hosts for the same
  definition.
- `ActorRef<M>` remains a message-only capability. It does not expose state or
  UI concepts.
- `ActorComponent<S, M>` is an optional `ui-actor` component definition. It
  attaches rendering to a page-hosted activation and dispatches its view's
  messages to that activation.
- State observation is a host control-plane operation used internally by
  `ActorComponent`; it is not expressed as `Subscribe` and `Unsubscribe`
  messages in every domain protocol.
- A page activation is owned by `PageScope`, while a rendered attachment is
  owned by `ComponentSegment`. Consequently, a component may remount without
  resetting its page model, and a WebSocket may reconnect without affecting
  either lifetime.
- A directory supplies logical IDs and REST discovery independently of the
  host. Closing a page administratively closes and unregisters its activation;
  the domain protocol does not need a `Close` message.

The first delivery will implement the page host. A local-actor component host
can follow, but the first delivery must prove portability by running the same
Life definition under both the existing actor testkit and the page-host test
harness.

## Research conclusions

The proposal follows several useful precedents while retaining this
framework's existing typed Java API:

1. [The Elm Architecture](https://guide.elm-lang.org/architecture/) separates
   model, messages, update, and view. A message produces a new model and the
   runtime renders it. Commands and subscriptions are runtime-managed effects,
   rather than mandatory bookkeeping in every domain protocol.
2. [Phoenix LiveComponent](https://hexdocs.pm/phoenix_live_view/Phoenix.LiveComponent.html)
   gives a component its own state and lifecycle while executing it in its
   parent LiveView process. A nested LiveView uses a separate process when
   isolation is actually required. This supports a lightweight page host as
   the default and a separate actor host as an explicit placement choice.
3. [Akka's actor model](https://doc.akka.io/libraries/akka-core/current/typed/guide/actors-intro.html)
   associates an actor with a mailbox, behavior, execution environment, and
   address, and processes one message at a time. Its
   [interaction API](https://doc.akka.io/libraries/akka-core/current/typed/interaction-patterns.html)
   keeps `ActorRef` focused on the accepted protocol. This argues against
   adding UI snapshot methods to every reference.
4. [XState actors](https://stately.ai/docs/actors) distinguish reusable actor
   logic from a running actor and make snapshots an actor-runtime facility.
   This is close to the desired distinction between `ActorDefinition` and a
   page or local activation, but `ActorComponent` should own the snapshot
   attachment so application code does not manually subscribe.
5. [Orleans grain identity](https://learn.microsoft.com/en-us/dotnet/orleans/grains/grain-identity)
   separates logical type/key identity from a physical activation. The current
   `ActorType` and `ActorId` already provide this useful boundary. The initial
   implementation remains in-JVM and must not claim Orleans-style location or
   restart transparency.

The goal is therefore not to make a component pretend to be a distributed
actor. It is to let actor logic run in the cheapest host that satisfies the
required lifetime and isolation.

## Current system and accidental complexity

The current Life path has two stateful execution units for one game:

```text
browser event
  -> page Reactor
  -> LifeComponent intent translation
  -> LocalActorSystem mailbox
  -> LifeGame state transition
  -> domain Subscribe subscriber set
  -> UiActorSink
  -> page Reactor
  -> component snapshot state
  -> render
```

This introduces application-visible machinery that is not part of Game of
Life:

- `Subscribe`, `Unsubscribe`, `Close`, and `Snapshot` protocol types;
- a subscriber set in `LifeGame.GameState`;
- `UiActors`, `UiActorBinding`, and `UiActorSink` calls in component code;
- a component `State` wrapper containing an optional actor snapshot;
- separate page-actor and mount-subscription cleanup paths.

The repository already has most of the infrastructure needed to remove that
machinery:

- `RedirectableEventsConsumer` buffers work produced during the initial HTTP
  render and redirects it to the live page `Reactor` after WebSocket handoff;
- `LivePageSession` serializes commands for one page;
- `PageScope` survives WebSocket detach and closes when the resumable page
  expires or shuts down;
- `ComponentSegment.own` provides the shorter mount lifetime;
- `ActorDefinition`, `ActorBehavior`, and `ActorEffect` already separate most
  actor logic from `LocalActorSystem.Cell`.

## Goals

1. Let a UI component use the complete actor transition/effect model without a
   second application actor or application-defined snapshot subscription.
2. Keep one authoritative model state per page activation.
3. Reuse exactly the same `ActorDefinition` in the page host and local actor
   host.
4. Expose the same `ActorRef<M>` to UI controls, HTTP routes, streams, and other
   actors.
5. Preserve page bootstrap, reconnect, expiry, component reconciliation, and
   static-render cleanup semantics.
6. Preserve actor semantics: bounded admission, one in-flight message,
   asynchronous behavior, ordered effect application, timers, processing
   receipts, stop, and passivation.
7. Keep `ui-core` and `ui-http` independent of actors. Actor-specific code
   remains in `ui-actor`.
8. Remove static UI actor helpers and actor-subscription mechanics from the
   Life application.

## Non-goals for the first delivery

- distributed placement or transparent cross-node migration;
- durable mailbox, durable timers, or durable state recovery;
- changing database transaction semantics;
- making all existing components actors;
- public arbitrary reads of actor internal state through `ActorRef`;
- multiple browser pages rendering the same page-hosted activation;
- hot migration of a live activation and its in-flight mailbox from one host
  to another.

"Replaceable host" initially means that the definition, protocol, effects,
routes, and tests are reusable. Moving a live activation, timers, or volatile
state requires an explicit snapshot/persistence design later.

## Terminology and ownership

| Term                 | Meaning                                                                  | Owner                         |
|----------------------|--------------------------------------------------------------------------|-------------------------------|
| Actor definition     | Reusable initial-state and message-transition logic                      | Application/static definition |
| Activation           | One live state plus mailbox, timers, and lifecycle                       | Selected host                 |
| Page actor runtime   | Application service that supplies scheduling, asks, and page activations | `ApplicationContext`          |
| Page activation      | Activation whose message turns execute on one page's event loop          | `PageScope`                   |
| Render attachment    | Internal state-commit listener that schedules component rendering        | `ComponentSegment`            |
| Directory entry      | Logical ID and reference used for discovery and routes                   | Directory until page closure  |
| WebSocket attachment | Current transport for an already-live page                               | `ResumablePageSession`        |

The WebSocket is deliberately absent from the actor lifetime. Detach and resume
must not reactivate or reset the model.

## Proposed architecture

```text
                         ActorDefinition<S, M>
                                  |
                   +--------------+---------------+
                   |                              |
          LocalActorSystem                  PageActorRuntime
          worker executor                   page command queue
                   |                              |
              ActorRef<M>  <same protocol>   ActorRef<M>
                                                  |
                          +-----------------------+------------------+
                          |                                          |
                    HTTP / streams                            ActorComponent
                                                               render attachment
                                                                     |
                                                               component view
```

### Portable definition

Retain `ActorDefinition<S, M>` as the public reusable definition rather than
adding a look-alike model hierarchy. Review each member with this rule:

- `type`, `initialState`, and `behavior` are portable and stay;
- mailbox capacity is portable if every host enforces it consistently;
- executor selection, scheduler implementation, placement, discovery, and
  page rendering belong to hosts and must not enter the definition;
- `ActorContext` may expose only capabilities implementable by every host;
- `ActorEffect` semantics must be identical in both hosts.

The current `passivating()` effect can be supported by both hosts: finish the
accepted message, reject/fail queued messages, cancel owned timers, unregister
the activation, and permit later activation according to host policy. Ordinary
page teardown is administrative and must not require a domain `Close` message.

### Message-only references

Keep this conceptual API:

```java
public interface ActorRef<M> {
    SendResult tell(ActorEnvelope<M> message);
    ProcessingReceipt track(ActorEnvelope<M> message);
    Optional<ActorId<M>> id();
}
```

Do not put `observe`, `getState`, `ComponentSegment`, or `StateUpdater` on
`ActorRef`. Not all actors have a meaningful render snapshot, and a distributed
reference cannot provide synchronous state access.

Instead, a host returns an internal or narrowly scoped handle:

```java
interface HostedActor<S, M> extends AutoCloseable {
    ActorRef<M> ref();
    S currentSnapshot();
    AutoCloseable attach(ActorStateObserver<S> observer);
}
```

`ActorComponent` consumes this control-plane handle. Normal application code
uses `ActorRef` only. The exact visibility of `HostedActor` should be minimized
during implementation; it need not become general public API in the first
increment.

### Ask/reply extraction

`ActorSystem` currently combines hosting, logical reference lookup, lifecycle,
and ask timeout support. Page-hosted references need ask/reply without using a
dummy local actor system. Extract the host-neutral part:

```java
public interface ActorGateway {
    <M, R> CompletionStage<R> ask(
            ActorRef<M> target,
            Function<ActorRef<R>, M> command,
            Duration timeout);

    <M, R> CompletionStage<R> askEnvelope(
            ActorRef<M> target,
            Function<ActorRef<R>, ActorEnvelope<M>> command,
            Duration timeout);
}

public interface ActorSystem extends ActorGateway, ApplicationLifecycle {
    <M> ActorRef<M> ref(ActorId<M> id);
    CompletionStage<Void> drainAndStop();
}
```

`LocalActorSystem` and `PageActorRuntime` implement `ActorGateway`.
`ActorRouteHandler` depends on `ActorGateway`, making the same routes usable for
either host.

### Shared activation engine

Do not independently reimplement actor ordering in `ui-actor`. Extract the
serialized cell mechanics from `LocalActorSystem.Cell` into a reusable runtime
engine under `actor-runtime`. It should accept host strategies for:

- scheduling the next mailbox turn;
- scheduling/cancelling delayed deliveries;
- resolving local outbound references;
- observing activation, processing, rejection, and failure;
- committing a new state snapshot;
- notifying termination/passivation.

`LocalActorSystem` supplies its executor-backed dispatcher. `PageActorRuntime`
supplies a dispatcher that enqueues one page command. `ui-actor` may therefore
depend on `actor-runtime`; this is justified because it is using the actor
execution engine, while `ui-core` remains independent.

Do not drain a complete mailbox in one page task. Execute at most one actor
turn per queued page task and enqueue the next turn at the tail. This prevents
a busy actor from starving DOM events or another page actor.

## Page-host execution semantics

### Admission

`tell` and `track` are thread-safe and may be called by HTTP worker threads,
stream callbacks, actor workers, timers, or the page itself.

1. Validate the message against `ActorType.messageClass()`.
2. Reject when the runtime is not running, activation is closed/stopped, or
   the mailbox is full.
3. Add an accepted envelope to the bounded activation mailbox.
4. If no turn is scheduled or in flight, enqueue exactly one page task.
5. `track` completes only after the transition is committed and its immediate
   local effects have been admitted, matching `LocalActorSystem`.

Messages admitted during initial HTTP rendering can be buffered behind the
existing `RedirectableEventsConsumer` until the page event loop is attached.
Page closure must fail their receipts even if the browser never connects.

### One in-flight transition without blocking the page

The behavior may return an incomplete `CompletionStage`.

1. The page turn removes one envelope and calls the behavior with the current
   authoritative state.
2. While its stage is incomplete, that activation processes no other message,
   but the page event loop continues processing unrelated DOM and model work.
3. Completion from any thread enqueues a completion command onto the page
   event loop.
4. Only that completion command may commit state, interpret effects, notify the
   render attachment, and schedule the next actor turn.
5. A completion arriving after activation closure is ignored for state/effects
   and settles its receipt as failed exactly once.

No behavior callback, state commit, or render attachment may run concurrently
for the same activation.

### Effect order

Match the existing local runtime:

1. validate a completed `ActorEffect`;
2. replace authoritative state if requested;
3. mark stop/passivation if requested;
4. admit or schedule outbound deliveries in declaration order;
5. publish the committed snapshot to attached renderers;
6. settle the processing receipt;
7. enqueue the next mailbox turn unless stopped.

The local runtime currently publishes its observer notification after outbound
delivery. During extraction, choose and test one ordering for both hosts. The
recommended order above publishes rendering after effects have been admitted
but before the receipt is completed, so a completed receipt means both model
state and its UI notification have been accepted. DOM rendering itself may
occur in a later page task.

### Timers

- The page runtime owns an application-scoped scheduler; timer callbacks never
  execute behavior directly.
- A timer callback sends its envelope back through the activation reference.
- Page closure, actor stop, actor failure, and passivation cancel all timers
  owned by that activation.
- Existing epoch/version techniques remain appropriate when a previously
  enqueued timer message must be made harmless after pause/reset.
- Move the scheduler contract needed by both runtimes to an appropriate shared
  package; do not make `ui-actor` depend on a test-only scheduler.

### Failure

- A behavior failure stops the activation, cancels timers, fails the current
  and queued receipts, rejects subsequent sends, and reports through actor
  observability.
- It does not roll back a transition already committed by an earlier message.
- A component render failure does not roll back authoritative actor state.
  The component may retain its last rendered snapshot and recover on a later
  commit; this behavior must be explicit and tested.
- Outbound rejection remains observable and does not silently mutate domain
  state.

## Component integration

### Per-segment runtime controller

`Component` is currently a reusable definition that also acts as callbacks and
intent handler for every segment. `ActorComponent` needs a per-segment handle,
so storing it in component fields would be incorrect.

Add a UI-neutral per-segment runtime seam to `ui-core`, for example:

```java
public interface ComponentRuntime<S, I>
        extends ComponentStateSupplier<S>,
                ComponentIntentHandler<S, I>,
                ComponentCallbacks<S> {
}
```

`Component.createComponentRuntime(...)` defaults to a delegating runtime using
the component's existing methods, preserving the normal component programming
model. `ComponentSegment` stores that runtime rather than three unrelated
shared objects. Creating the runtime must remain side-effect free because a
reconciliation candidate may be discarded. It may lazily open resources only
when its state supplier runs for the winning segment or when it mounts.

`ActorComponent` supplies an `ActorComponentRuntime` that:

1. opens or reuses the page activation while resolving initial state;
2. returns the activation's current immutable snapshot for initial rendering;
3. attaches a render listener after the winning segment mounts;
4. sends view-dispatched messages to the activation reference;
5. detaches the listener on unmount without stopping the page activation.

### Application-facing API

The target API is intentionally small; signatures below are illustrative and
should be validated with the Life spike:

```java
public abstract class ActorComponent<S, M> extends Component<S, M> {
    protected abstract ActorDefinition<S, M> definition();

    protected abstract PageActorPlacement<M> placement(
            ActorComponentContext context);

    @Override
    public abstract ComponentView<S, M> componentView();
}
```

The view dispatches actor messages directly. The base class finalizes initial
state and intent handling; application components do not implement
`onMounted`, subscribe, unsubscribe, or project snapshots.

`PageActorPlacement` combines a logical ID/directory reservation with the page
host selection. It should not expose `ComponentSegment` or `StateUpdater`.
For a simple, non-discoverable actor component, the framework can derive a
page-local identity. A discoverable component supplies a directory:

```java
PageActorDirectory<Long, LifeGame.Command> games =
        PageActorDirectory.numbered(pageActors, LifeGame.TYPE);
```

The final API should optimize the simple case while making public identity an
explicit application choice. Do not allocate public IDs implicitly for every
component.

### Render attachment

The activation owns `S`. A component segment holds only the immutable snapshot
reference currently used to render and diff its DOM. That reference is a
rendering cache, not another independently mutable domain state.

On attachment, the runtime must immediately offer the current snapshot. On
subsequent state commits, it should coalesce unrendered snapshots to the latest
value, as the earlier `UiActorSink.latest` adapter did. This is valid for immutable
full state snapshots, not for event streams. Domain events that must all be
observed still use messages or the stream API.

Attachment and detachment are framework lifecycle operations. They do not
enter the actor mailbox and do not appear in the domain protocol.

### Component reconciliation

- A discarded candidate must not create or attach an activation.
- Reusing a component segment preserves its attachment.
- Replacing/unmounting a segment detaches it.
- A later mount for the same page/directory attaches to the existing activation
  and renders its current state.
- The activation stops only when its `PageScope` closes, unless its own effect
  explicitly stops/passivates it.

## Directory and addressing

Refactor `PageActorDirectory` from "create a local actor and send it a close
message" into a host-neutral page activation catalog.

Required behavior:

- allocate or accept a logical key;
- reserve `(ActorType, key)` before activation initialization;
- atomically publish the page-hosted reference after successful activation;
- reuse the entry for the same `QualifiedSessionId` and `PageScope`;
- reject duplicate IDs without replacing the existing entry;
- expose `find` and creation-ordered `all` for routes;
- remove the entry when its exact activation closes;
- close stale reservations safely;
- never create unknown actors as a side effect of a route lookup.

The directory must not require a domain close-message supplier. `PageScope`
owns the activation directly, and administrative close rejects further sends,
cancels timers, and removes the directory entry.

Logical identity and authorization remain separate. A numeric ID makes the
Life demo discoverable; it does not make arbitrary page actors safe to expose.

## Lifecycle matrix

| Event                         | Activation                         | Render attachment                      | Directory | Timers/mailbox                     |
|-------------------------------|------------------------------------|----------------------------------------|-----------|------------------------------------|
| Initial HTTP render succeeds  | Created/reused                     | Attached after mount                   | Published | Work may buffer until live handoff |
| Reconciliation reuses segment | Preserved                          | Preserved                              | Preserved | Preserved                          |
| Component unmount/remount     | Preserved                          | Detached/new attachment                | Preserved | Preserved                          |
| WebSocket detach/resume       | Preserved                          | Preserved                              | Preserved | Continues while page remains live  |
| Initial page never connects   | Closed on pending-page expiry      | Detached by tree shutdown              | Removed   | Timers cancelled, receipts failed  |
| Static render completes       | Closed                             | Detached                               | Removed   | Cancelled/failed                   |
| Page expires or shuts down    | Closed after component unmount     | Detached first                         | Removed   | Cancelled/failed                   |
| Actor effect stops/passivates | Stopped                            | Receives terminal host signal/detaches | Removed   | Cancelled/failed                   |
| Application stops             | Page sessions close before runtime | Detached                               | Cleared   | Runtime drains or fails by policy  |

The existing server shutdown order already closes page sessions before the
application context; preserve and test that order.

## Target Life design

`LifeGame` becomes a portable definition with renderable state:

```java
public final class LifeGame {
    public record State(
            GameSummary summary,
            Board board,
            long epoch) {
    }

    public sealed interface Command
            permits Control, ToggleCell, Status, Tick {
    }

    public static ActorDefinition<State, Command> definition(
            RandomGenerator random) {
        // Same state transitions, self-scheduled ticks, and replies.
    }
}
```

Remove `Snapshot`, `Subscribe`, `Unsubscribe`, `Close`, and the subscriber set.
`Status` and reply-bearing `Control` remain because they are genuine
request/response protocol operations used by HTTP.

The component becomes approximately:

```java
final class LifeComponent
        extends ActorComponent<LifeGame.State, LifeGame.Command> {
    private final PageActorDirectory<Long, LifeGame.Command> games;
    private final ActorDefinition<LifeGame.State, LifeGame.Command> game;

    @Override
    protected ActorDefinition<LifeGame.State, LifeGame.Command> definition() {
        return game;
    }

    @Override
    protected PageActorPlacement<LifeGame.Command> placement(
            ActorComponentContext context) {
        return context.in(games);
    }

    @Override
    public ComponentView<LifeGame.State, LifeGame.Command> componentView() {
        return commands -> state -> /* render and dispatch commands */;
    }
}
```

This example has no `onMounted`, `State.loading()`, actor/UI projection,
subscription result handling, or intent-to-command translation. Initial state
is available for the initial HTML render.

`LifeRoutes` changes only at the host seam:

- accept `ActorGateway` rather than `ActorSystem` for ask/reply;
- resolve `ActorRef<LifeGame.Command>` from the same directory;
- retain the numeric routes and JSON response format.

`Life` owns `PageActorRuntime` through `ApplicationContext`. It no longer needs
a `LocalActorSystem` merely to run one actor per page. The same Life definition
is still registered with `ActorTestKit` for portable behavior tests.

## Implementation sequence

### 1. Characterize portable actor semantics

- Add parity tests around current `LocalActorSystem.Cell`: admission,
  asynchronous completion, state-before-effects ordering, tracked completion,
  self-send, timers, failure, stop, and passivation.
- Record which behaviors are intentional before extracting the cell.
- Decide and document whether a rendering notification is considered an
  immediate local effect for `ProcessingReceipt` ordering.

Exit criterion: the shared engine can be refactored without relying on
undocumented behavior.

### 2. Separate messaging from local hosting

- Add `ActorGateway` to `actor-api`.
- Make `ActorSystem` extend it.
- Change `ActorRouteHandler` to accept `ActorGateway`.
- Move the scheduler contract required for asks and delayed actor effects to a
  shared runtime/API location.
- Keep route status/error mappings unchanged.

Exit criterion: all current actor and HTTP tests pass with no page code added.

### 3. Extract the shared serialized activation engine

- Extract the mailbox/in-flight/state/effect logic from
  `LocalActorSystem.Cell`.
- Parameterize execution dispatch, timer scheduling, outbound delivery,
  observability, committed-state notification, and termination.
- Rebuild `LocalActorSystem` on that engine.
- Extend actor-testkit so the same definition can be driven deterministically
  under more than one dispatcher.

Exit criterion: local-runtime behavior is unchanged and there is only one
implementation of core actor turn semantics.

### 4. Add the page actor runtime and host-neutral directory

- Add `PageActorRuntime` to `ui-actor`, backed by the shared activation engine.
- Dispatch actor turns and async completions through `CommandsEnqueue`.
- Own scheduler/application resources through `ApplicationContext` and each
  activation through `PageScope`.
- Refactor `PageActorDirectory` to reserve/publish page-hosted references and
  close them administratively.
- Implement `ActorGateway` asks for page-hosted references.
- Add deterministic tests for bootstrap buffering, page fairness, async
  completion, close races, timer cancellation, and directory cleanup.

Exit criterion: a page-scoped definition can be addressed and controlled over
HTTP without a component or local actor system.

### 5. Add the per-segment component runtime seam

- Introduce the UI-neutral `ComponentRuntime<S, I>` (final naming subject to
  implementation review).
- Make ordinary `Component` create a delegating runtime so existing component
  behavior stays unchanged.
- Ensure discarded reconciliation candidates remain side-effect free.
- Add focused lifecycle/reconciliation tests before actor integration.

Exit criterion: a component subclass can safely own a distinct controller for
each mounted segment without mutable fields on the reusable definition.

### 6. Implement `ActorComponent`

- Add `ActorComponent`, its lazy per-segment runtime, placement context, and
  internal render attachment to `ui-actor`.
- Initialize rendering from the activation's current state.
- Dispatch view messages directly to `ActorRef`.
- Coalesce committed full snapshots on the page queue.
- Detach on unmount and retain the page activation.
- Define hooks or metrics for UI-originated admission rejection and activation
  failure without forcing operational errors into every domain state.

Exit criterion: a test component has one authoritative actor state, no domain
subscription messages, and survives remount/reconnect as specified.

### 7. Migrate Life as the design proof

- Make Life state public/renderable and delete snapshot subscriber state.
- Delete `Subscribe`, `Unsubscribe`, and `Close` commands.
- Replace the component snapshot wrapper and intent translation with direct
  command dispatch from an `ActorComponent` view.
- Run Life through `PageActorRuntime`; keep portable definition tests through
  `ActorTestKit`/`LocalActorSystem`.
- Update REST wiring to `ActorGateway` and the host-neutral directory.
- Remove obsolete `UiActors`, `UiActorBinding`, and `UiActorSink` only after
  searching for remaining consumers. If generic external-actor UI observation
  still has valid consumers, retain it as a separately named adapter rather
  than as the ActorComponent path.

Exit criterion: Life has fewer application types and lifecycle operations than
the current implementation while preserving UI and REST behavior.

### 8. Stabilize API and documentation

- Compare page and local host semantics in tests and Javadocs.
- Document placement tradeoffs: page host for page-bound state; local actor for
  independent lifetime, isolation, or non-UI consumers; persistent/distributed
  hosts later.
- Update the actor reference, module map, examples, and prior page-actor plan.
- Run focused module tests, examples/E2E tests, full `mvn test`, Javadocs, and
  `git diff --check`.

## Required test matrix

### Shared definition parity

- identical initial state under local and page hosts;
- identical state/effects for the same envelope sequence;
- same immediate outbound delivery order;
- same processing receipt success/failure boundary;
- same stop/passivation and timer cancellation semantics.

### Page runtime

- concurrent senders cannot process two messages concurrently;
- incomplete behavior does not block unrelated page events;
- completion is marshalled back to the page thread;
- only one drain task is scheduled per activation;
- one actor cannot drain an unbounded batch ahead of DOM work;
- mailbox capacity and stopped/not-started admission are correct;
- page close fails current/queued tracked sends exactly once;
- late stage/timer callbacks cannot resurrect a closed activation.

### Component lifecycle

- initial HTML renders the definition's initial state, not a loading shell;
- view message causes one actor transition and one resulting render;
- remount attaches to existing page state;
- unmount prevents later DOM updates but does not stop the activation;
- page closure stops it and removes discovery;
- WebSocket detach/resume preserves state and identity;
- failed/static/unconnected pages do not leak activation or directory entries;
- discarded reconciliation candidates do not allocate actors;
- a render exception does not roll back actor state or corrupt the next render.

### Life behavior and E2E

- separate pages have separate numeric game IDs and boards;
- REST discovery exposes only live page games;
- REST start/pause/reset and UI controls affect the same state;
- start remains idempotent and old ticks remain invalidated;
- page closure yields 404 and the old reference rejects messages;
- no Life protocol type or test mentions subscription/unsubscription;
- optional browser test verifies disconnect/resume and eventual expiry.

## Risks and design checks

1. **Two schedulers or two actor-cell implementations.** Avoid by extracting a
   shared engine before building the page host.
2. **Resource creation in discarded component candidates.** Avoid with a lazy
   per-segment runtime and explicit reconciliation tests.
3. **Confusing page lifetime with mount or WebSocket lifetime.** Keep the
   ownership matrix in public Javadocs and tests.
4. **Blocking the page on asynchronous behavior.** Never join a behavior stage;
   marshal completion back as a page command.
5. **Page starvation.** Schedule one message turn at a time, not a mailbox
   drain loop.
6. **Pretending host replacement is live migration.** Promise definition/API
   portability only until durable snapshots and activation transfer exist.
7. **Exposing internal state through references.** Keep snapshots on the host
   handle/control plane; keep `ActorRef` message-only.
8. **Hidden public actor exposure.** Require explicit directory/identity for
   REST discovery; default component actors remain private to their page.
9. **Operational errors polluting domain state.** Add actor-component failure
   hooks/metrics before inventing a mandatory `State<Result<...>>` wrapper.
10. **Rendering every high-frequency snapshot.** Coalesce render snapshots,
    while retaining mailbox processing and domain-event guarantees.

## Acceptance criteria

The design is successful when all of these are true:

- Life's `ActorDefinition` runs unchanged in local actor tests and the page
  host.
- Life contains no subscription protocol, subscriber collection, close
  command, UI actor helper call, or mirrored snapshot wrapper.
- The component view dispatches typed actor commands and renders actor state.
- REST routes address the exact same activation through `ActorRef`.
- Page reconnect preserves identity/state; page close stops and unregisters
  the activation.
- The page runtime matches local actor ordering, failure, timer, and receipt
  semantics covered by parity tests.
- `ui-core` and `ui-http` have no dependency on `actor-api` or `actor-runtime`.
- Application code is materially smaller than the current Life implementation;
  framework-internal lifecycle machinery is tested and reusable.
