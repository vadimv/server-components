# Page sessions and actors: implemented integration

The current integration hosts an actor directly on its page event loop. A
component is a render attachment to that activation, not a second stateful
unit and not a domain-protocol subscriber.

## Ownership model

| Lifetime | Owner | Resource | End condition |
| --- | --- | --- | --- |
| Application | `ApplicationContext` | `PageActorRuntime` scheduler and asks | Application shutdown |
| Page session | `PageScope` | Page actor activation and directory entry | Failed/static render, unconnected-page expiry, or live-session shutdown |
| Component mount | `ComponentSegment.own` | Coalescing render attachment | Component unmount or replacement |
| WebSocket attachment | `ResumablePageSession` | Transport only | Detach/replacement; actor and component survive during the resume grace period |

## Implemented model

1. `actor-runtime` contains one `SerializedActorActivation` engine for bounded
   mailboxes, one-at-a-time turns, async completion, effects, receipts, and
   termination. `LocalActorSystem` and `PageActorRuntime` supply different
   execution hosts.
2. `PageActorRuntime` queues each turn and completion through the page's
   `CommandsEnqueue`. Initial state is available for server rendering; queued
   behavior starts when the page event loop is attached.
3. `PageActorDirectory` allocates optional public keys, reuses the exact
   activation for a page and scope, and removes it on administrative page
   close. Domain protocols do not need a close message.
4. `ActorComponent<S, M>` lazily resolves placement during the first real
   render, renders the activation's current `S`, sends view messages directly
   to `ActorRef<M>`, and coalesces committed immutable states while a render is
   queued.
5. A component unmount detaches rendering but leaves the page actor alive. A
   page close stops the actor, rejects late sends, cancels timers, and removes
   discovery.

`ui-core` remains actor-neutral. Its `ComponentRuntime<S, I>` seam permits a
per-segment controller without adding actor dependencies to every component.

## Life proof

Game of Life uses the same `ActorDefinition<LifeGame.State, LifeGame.Command>`
under `LocalActorSystem` tests and the page host. `LifeComponent` has no loading
wrapper, mount callback, subscription protocol, or intent-to-command adapter.
REST routes address the same page-hosted reference through `ActorGateway`.

Page-hosted actors are appropriate for page-bound state. Their turns begin
when the live page event loop is handed off, so an HTTP ask to a page that has
rendered but never connected can time out. Use `LocalActorSystem` for actors
that must progress independently of a browser page.
