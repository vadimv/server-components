# Page sessions and actors: implemented integration

The current integration hosts an actor directly on its page event loop. A
component is a render attachment to that activation, not a second stateful
unit and not a domain-protocol subscriber.

## Ownership model

| Lifetime             | Owner                  | Resource                                  | End condition                                                                  |
|----------------------|------------------------|-------------------------------------------|--------------------------------------------------------------------------------|
| Application          | `ApplicationContext`   | `PageActorRuntime` scheduler and asks     | Application shutdown                                                           |
| Page session         | `PageScope`            | Page actor activation and directory entry | Failed/static render, unconnected-page expiry, or live-session shutdown        |
| Component mount      | `ComponentSegment.own` | Coalescing render attachment              | Component unmount or replacement                                               |
| WebSocket attachment | `ResumablePageSession` | Transport only                            | Detach/replacement; actor and component survive during the resume grace period |

## Implemented model

1. `actor-runtime` contains one `SerializedActorActivation` engine for bounded
   mailboxes, one-at-a-time turns, async completion, effects, receipts, and
   termination. `LocalActorSystem` and `PageActorRuntime` supply different
   execution hosts.
2. `PageActorRuntime` queues each turn and completion through the page's
   `CommandsEnqueue`. Initial state is available for server rendering; registration
   starts the page event loop after the HTML snapshot is complete. Queued turns,
   asynchronous completions, and timers run before WebSocket attachment.
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

Page-hosted actors are appropriate for page-bound state. HTTP asks and controls
can progress after rendering even when the browser has not connected. The first
WebSocket attaches to the existing session and receives buffered updates without
recreating its actors. Unattached pages expire after the configured grace period;
output overflow and shutdown also release the page scope. Use `LocalActorSystem`
for actors whose lifetime extends beyond a page.

Session construction is separate from startup. The registry publishes ownership
before starting execution, and closes rejected or failed registrations exactly
once. `pagesStorage` remains an index of pages awaiting their first attachment;
the registry owns all running sessions. Session metrics include unattached pages.
