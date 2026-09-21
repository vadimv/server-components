# Page sessions and actors: implemented integration

## Ownership model

The actor remains an application actor. A UI component is its presentation and
delivery adapter; the component and actor do not share a mailbox or state model.

| Lifetime | Owner | Resource | End condition |
| --- | --- | --- | --- |
| Page session | `PageScope` | Per-page actor registration | Page render failure, static render completion, unconnected-page expiry, or live-session shutdown |
| Component mount | `ComponentSegment.own` | `UiActorBinding` (snapshot sink and subscription) | Component unmount, including reconciliation |
| WebSocket attachment | `ResumablePageSession` | Transport only | Detach/replacement; does not close actor or component |

The scope is placed in `ComponentContext` by `PageHttpHandler`. A component can
obtain it during `onMounted`, register a page-owned resource, and independently
register a mount-owned resource with the segment. A reusable component must keep
its own context semantics in mind; `onMounted` is not repeated for a reused
segment.

## Implemented sequence

1. `ui-core` provides idempotent, reverse-order `PageScope` cleanup and a
   `ComponentSegment.own` hook. Page cleanup runs after component unmount.
2. `ui-http` tracks rendered-but-unconnected pages for the configured local
   resume grace period, cancels that expiry on WebSocket handoff, and closes
   pending pages on expiry or server stop. Failed and static renders unwind
   immediately.
3. `ui-actor` provides `UiActorBinding`, which subscribes a coalescing snapshot
   sink and closes the sink before requesting unsubscribe. It depends only on
   `actor-api` and `ui-core`; neither core nor HTTP depends on actors.
4. Life assigns one game actor to each page scope. A component mount owns only
   its subscription. Remounting can reuse the same game actor; a WebSocket
   reconnect does not alter either lifetime.
5. Tests cover scope ordering, pending-page expiry, handoff, static/failed
   renders, actor binding teardown, and Life's remount-versus-page-close behavior.

## Optional `ui-actor` facade

The common actor-backed page can use `PageActorDirectory.numbered(...)` to
allocate discoverable IDs, reuse an actor across remounts, and request its
close when the page ends. `UiActors.observe(...)` creates a coalescing snapshot
binding and owns it on the component segment only when subscription admission
succeeds. The caller still handles `SendResult` and domain-specific UI errors.
Both types live in `ui-actor`; `ui-core` and `ui-http` remain actor-agnostic.

Life now uses the directory directly for REST discovery and the two facade
calls in its mount callback, removing the example-specific `LifeGames`
registry. Its actor behavior and page markup remain explicit.

## Delivery boundary

The current actor runtime uses bounded, at-most-once local mailboxes. An
unsubscribe can be rejected under pressure; its sink is still closed so late
snapshots cannot update the unmounted component. Likewise, the directory
requests actor passivation with an ordinary close message. Stronger
administrative stop or delivery-retry semantics would be a separate runtime
change, not something the page scope should silently pretend to guarantee.
