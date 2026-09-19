# Web foundation: implementation plan for step 7

## Goal and current problem

Steps 1–6 separated URL, HTTP, WebSocket, transport, and UI concerns. The
remaining application assembly API is still UI-owned: `Config`, `Services`,
and service lifecycle hooks live in `compositions`, while `AppComponent`
starts shared service instances for every rendered browser page. A singleton
service can therefore be started and stopped concurrently by unrelated user
sessions. Composition services have the same mismatch at scene scope.

Step 7 introduces one UI-independent application context and gives it an
explicit process lifecycle. Request, page, component, and scene lifecycles do
not own application services.

## Resulting ownership

```text
application-api
  ^       ^                 ^
  |       |                 |
http-api  compositions      ui-http
  ^                             ^
  |                             |
server-socket ------------------+
```

`application-api` has no project dependencies. It owns immutable configuration,
typed service registration, and application lifecycle contracts. HTTP and UI
modules may adapt that lifecycle, but the context imports neither transport nor
component types.

## 7.1 Application context

Add these public contracts:

- `ApplicationConfig`: immutable layered string configuration with typed
  accessors.
- `ApplicationLifecycle`: idempotent `start`, `stop`, and `close` vocabulary.
- `ApplicationContext`: immutable typed service registry plus lifecycle owner.

The context builder validates that a service instance implements its declared
key and rejects duplicate keys. Services implementing `ApplicationLifecycle`
start once in deterministic registration order and stop once in reverse order.
The same instance registered under multiple interfaces is managed once.

Startup is transactional: if one service fails, already-started services are
stopped in reverse order and the original failure is rethrown with cleanup
failures suppressed. Shutdown attempts every service and aggregates failures.
A stopped context cannot be restarted.

## 7.2 Host integration

`HttpApplication` and `PageApplication` extend the lifecycle contract while
remaining functional interfaces through default no-op lifecycle methods. Each
provides a lifecycle-preserving wrapper for handler lambdas.

`SocketWebServer` starts its `HttpApplication` before accepting requests, rolls it
back if binding fails, and stops it only after connections and WebSockets have
closed. `ui-http.WebServer` similarly brackets its page application, but closes
live UI sessions before stopping application services.

Authentication page adapters retain the wrapped `App` lifecycle instead of
returning a lambda that loses it.

## 7.3 UI projection and migration

`compositions.App` receives an `ApplicationContext` and delegates host lifecycle
to it. `AppComponent` only projects immutable configuration, the context itself,
and typed services into `ComponentContext`; mounting or unmounting a page has no
effect on application services.

Remove `compositions`-owned `Config`, `Services`, `ServiceMapLookup`, and
`ServicesLifecycleHandler`. Remove composition-level service lifecycle because
the registered instances are shared configuration, not scene-scoped factories.
True page or block resources continue to use component lifecycle hooks.

Migrate the admin example's scheduler-backed services to
`ApplicationLifecycle`, register them in the application context, and delete
manual startup calls. Server shutdown must stop those schedulers.

## Exit criteria

- Application configuration and lifecycle have no UI or HTTP dependency.
- Typed services are immutable after build and lifecycle order, rollback,
  duplicate aliases, idempotence, and failure aggregation are unit-tested.
- Application services start once for any number of page renders and stop only
  when the host stops.
- Generic REST applications can attach a lifecycle to `HttpApplication`.
- UI authentication adapters preserve the `App` lifecycle.
- No scene or component callback starts or stops application services.
- Server, compositions, authentication, and all browser E2E tests pass.
- The module-boundary audit enforces the new lower-layer ownership.

## Implemented result

Step 7 is complete. The new `application-api` module owns
`ApplicationConfig`, `ApplicationLifecycle`, and `ApplicationContext` without
depending on another project module. HTTP and page applications now retain
lifecycle through explicit wrappers, and both server hosts bracket their
applications in the required startup and shutdown order.

`compositions.App` now accepts the shared context. Components only receive a
projection of its configuration and typed services; page and scene callbacks
no longer control process services. The former compositions-specific config,
service registry, lookup, and lifecycle types were removed. Authentication
adapters and the examples use the new contracts, including scheduler-backed
services that are now stopped by server shutdown.

Lifecycle ordering, rollback, alias deduplication, idempotence, host ordering,
context projection, and authentication preservation have automated coverage.
The complete examples E2E suite also passes; its relationship-selector check
now waits for the server-rendered dirty-state patch before testing Cancel,
removing an existing full-suite timing race.
