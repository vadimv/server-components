# Web foundation: implementation plan for steps 1–4

## Goal and boundary

These four steps establish reusable URL/routing and HTTP contracts while making the UI model independent of HTTP response state. They deliberately break the old `core`, `http`, `rsp.server.http`, and compositions `Router` APIs.

The transport extraction is staged. At the end of step 4, `ui-http` owns the existing UI adapter and may still contain the JDK socket implementation so the application remains runnable. `server-jdk` is present as the transport destination but is populated in the next implementation phase. This temporary placement must not create a dependency from `ui-core` to `http-api` or `ui-http`.

## Target dependency rules

```text
url                 (no project dependencies)
http-api        --> url
http-routing    --> http-api
http-json       --> http-api, json
websocket-api       (no UI dependency)
ui-core         --> url, json
compositions    --> ui-core, url, authorization, schema
ui-http         --> ui-core, http-api, js-client(runtime)
ui-http-auth    --> ui-http, compositions, json
server-jdk      --> http-api, websocket-api       [populated after step 4]
```

Forbidden edges after step 4:

- `url` to HTTP, JSON, UI, or a server implementation.
- `http-api` to UI, compositions, or a server implementation.
- `ui-core` to `http-api`, `ui-http`, or a server implementation.
- `compositions` to `ui-http` or a server implementation.
- `ui-http` to compositions or an optional authentication provider.

## Step 1 — establish module boundaries

### Changes

1. Rename Maven artifact/directory `core` to `ui-core` and `http` to `ui-http`.
2. Add reactor modules `url`, `http-api`, `http-routing`, `http-json`, `websocket-api`, `ui-http-auth`, and `server-jdk`.
3. Update every internal consumer to use `ui-core` and `ui-http` coordinates.
4. Declare only the dependency edges shown above. Empty destination modules are acceptable until their corresponding step moves code into them.
5. Add a reactor dependency check that fails if the old `core` or `http` artifact coordinates reappear or if `ui-core` imports `rsp.http`.

### Exit criteria

- `mvn -DskipTests package` resolves the complete reactor.
- No POM refers to artifacts named `core` or `http`.
- The build exposes the new artifacts, with no compatibility facade for the old ones.

## Step 2 — URL values and generic routing

### URL API

Move `Path`, `Query`, `Fragment`, and `RelativeUrl` to `rsp.url` in `url`.

- Values are immutable and make defensive copies.
- `Path.parse(rawPath)` percent-decodes individual segments as UTF-8; `Path.of(...)` is the decoded-path convenience factory.
- Path encoding never applies form semantics (`+` remains `+`).
- `Query.parse(rawQuery)` applies query/form decoding and preserves duplicate keys and insertion order.
- `RelativeUrl` has no dependency on an HTTP request type.

### Routing API

Add these immutable, generic primitives to `rsp.url.routing`:

- `RouteTemplate`: parsed literal and `{name}` segments, named expansion, positional expansion, and parent calculation.
- `PathMatch`: immutable named parameter map.
- `RouteMatch<T>`: target, template, and parameters.
- `RouteTable<T>`: immutable table built by a builder, match, reverse lookup, and parent lookup.

Selection is deterministic: more literal segments win. Two templates that can match the same path with equal specificity are rejected when the table is built. Duplicate parameter names and non-absolute templates are rejected.

### Composition migration

Delete the mutable compositions `Router`. A `Composition` receives `RouteTable<BlockTarget>`, and route targets are the same typed values used by `Group`. Address-bar synchronization, scene construction, and navigation use `RouteMatch` parameters and `RouteTemplate.expand(...)`; none parse route strings themselves.

### Exit criteria

- URL and routing unit tests cover decoding, duplicate query parameters, named extraction, encoded expansion, precedence, ambiguity rejection, reverse lookup, and parents.
- Compositions contain no router implementation and import routing only from `rsp.url.routing`.
- Registration order cannot change route selection.

## Step 3 — transport-neutral HTTP contracts

Add the `rsp.http` API in `http-api`:

- `HttpMethod` and validated/extensible `HttpStatus`.
- immutable, case-insensitive `HttpHeaders` with repeated values.
- `MediaType`, request `Cookie`, and response `SetCookie` values.
- bounded, byte-oriented `RequestBody` and repeatable/streaming `ResponseBody`.
- immutable `HttpRequest` containing method, raw target, decoded `Path`, `Query`, headers, and body.
- immutable `HttpResponse` plus a builder/DSL for status, headers, cookies, text/bytes/stream bodies, and redirects.
- `HttpApplication` as `HttpRequest -> CompletionStage<HttpResponse>`.

The API must not reference sockets, JDK `HttpServer`, UI components, page sessions, or compositions. UTF-8 is the default for text helpers, and body limits are explicit rather than hidden in parsers.

The existing UI server/parser/writer is migrated to these contracts during this step. All request methods reach the application; method restrictions belong in routing/application policy rather than the socket loop.

### Exit criteria

- Contract tests cover header case folding/repetition, body limits, UTF-8, redirects, cookies, raw-target preservation, and streamed bodies.
- `http-api` has only the `url` project dependency.
- The current UI server compiles against `rsp.http` and does not use `rsp.server.http`.

## Step 4 — make `ui-core` HTTP-free

### Pure rendering model

- `HtmlDocument` represents only an HTML document. Remove status, headers, `addHeader`, `statusCode`, and `redirect`.
- `PageBuilder` produces rendering/session data only; remove response status/header accumulation.
- Remove `HeadType` and the special `PlainTag` head behavior. Introduce explicit page modes at the adapter boundary: `Pages.live(component)` and `Pages.staticHtml(component)`.
- Live-page bootstrap/script injection is selected by a render option supplied by `ui-http`, not inferred from a special DOM node.
- Move HTTP request access out of `ComponentContext`; an initial page handler in `ui-http` receives `HttpRequest` and can place explicit application-defined values in UI context where necessary.
- Replace HTTP-named UI exceptions with UI concepts (`PageNotFoundException`, `ComponentAccessDeniedException`); `ui-http` maps them to HTTP responses.
- Move the page-to-response handler and static-resource response mapping to `ui-http`.
- Put the optional compositions-aware authentication adapters in `ui-http-auth`, preserving the generic `ui-http` boundary.

### GUI response control

Initial GUI navigation returns an HTTP envelope from `ui-http`, so application code can redirect or add headers without mutating HTML:

```java
request -> page route -> PageResult
                         |- PageResult.render(component, response -> response.header(...))
                         `- PageResult.redirect(location)
```

After a live session is established, navigation is a browser command/event, not an attempted change to the already-sent HTTP response.

### Exit criteria

- `HtmlDocument` and `PageBuilder` have no status/header fields or methods.
- `rg 'rsp\.http|rsp\.server\.http' system/ui-core/src` returns no matches.
- Static rendering does not inject the live client; live rendering does.
- Redirect/header tests target the `ui-http` page result/response layer.
- Reactor compilation and all non-socket tests pass.

## Implementation checkpoints

Each step is implemented as a separately reviewable group of changes, but the working tree is kept compilable at each checkpoint. The final verification is:

1. `mvn -DskipTests package` for dependency and compilation integrity.
2. Unit tests for `url`, `http-api`, `ui-core`, and compositions.
3. Non-socket `ui-http` tests (parser, writer, session and frame logic).
4. A dependency/import audit using `rg` and Maven's reactor graph.

Socket-listener integration tests require an environment permitted to bind loopback ports; they are recorded separately when the sandbox blocks them.

## Implemented result

The step 1–4 work is now reflected in the repository as follows:

| Step | Delivered code | Enforced invariant |
| --- | --- | --- |
| 1 | Renamed `ui-core`/`ui-http`; added the URL, HTTP, WebSocket, auth-adapter, and future JDK-server modules | `scripts/check-web-module-boundaries.sh` runs in CI and rejects legacy coordinates and forbidden imports |
| 2 | `rsp.url` values, `rsp.url.routing`, `BlockRoutes`, typed `BlockTarget`, and migrated compositions/examples | Route tables are immutable, literal precedence is deterministic, and ambiguous equal-specificity routes fail at build time |
| 3 | `rsp.http` request/response contracts plus migrated parser/writer | Headers retain repeats case-insensitively, bodies are explicitly bounded or streamed, raw targets survive parsing, and all standard methods reach application policy |
| 4 | Pure `HtmlDocument`, adapter-selected live/static rendering, `PageApplication`/`PageResult`/`Pages`, and `ui-http-auth` | Initial request logic can redirect, set status/headers/cookies, or return a direct response before rendering; UI core and compositions import no HTTP API |

The intentionally empty `http-routing`, `http-json`, `websocket-api`, and
`server-jdk` source sets reserve stable dependency destinations. Populating
those modules—without moving transport dependencies upward—is the next phase,
not hidden work in these four steps.
