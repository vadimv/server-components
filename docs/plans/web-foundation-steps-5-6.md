# Web foundation: implementation plan for steps 5–6

## Goal and starting point

Steps 1–4 established immutable URL values, transport-neutral HTTP contracts,
an HTTP-free UI core, and explicit page-result adapters. Steps 5–6 turn those
foundations into a usable REST stack and complete the transport ownership split.

The implementation intentionally does not preserve the temporary placement of
the socket server in `ui-http`. Generic HTTP/1.1 and RFC 6455 mechanics move to
`server-jdk`; UI page rendering, resumable page sessions, and the RSP WebSocket
protocol remain in `ui-http`.

## Resulting dependency graph

```text
url
  ^
  |
http-api <---------------- websocket-api
  ^  ^                           ^
  |  |                           |
  |  +----- http-json            |
  |                              |
  +-------- http-routing         |
  |                              |
  +------------------------- server-jdk
                                 ^
                                 |
ui-core <--------------------- ui-http
  ^                              |
  +--------- compositions        +-- js-client (runtime)
                 ^               |
                 +--------- ui-http-auth
```

`server-jdk` may depend on `http-api` and `websocket-api`, but never on UI,
compositions, JSON, or routing. REST applications can choose only the modules
they need. `http-routing` and `http-json` do not depend on one another.

## Step 5 — REST routing and JSON integration

### 5.1 Method-aware HTTP routing

Populate `http-routing` with these contracts:

- `HttpRouteHandler`: asynchronous handler receiving an `HttpRequest` and an
  immutable `HttpRouteContext`.
- `HttpRouteContext`: the matched `RouteTemplate` and decoded named path
  parameters, with required/optional lookup helpers.
- `HttpRouter`: immutable `HttpApplication` assembled through a builder.

The builder provides `get`, `post`, `put`, `patch`, `delete`, `options`,
`head`, and general `route` methods. It delegates path parsing, specificity,
encoding, and ambiguity checks to `url.routing`; it does not implement a second
path-template language.

Dispatch rules are explicit:

1. Match the request method and path using deterministic route tables.
2. `HEAD` falls back to a matching `GET` route when no explicit `HEAD` route
   exists; the transport still suppresses the response body on the wire.
3. A known path with no handler for the method returns `405` and a sorted,
   de-duplicated `Allow` header. `HEAD` is included whenever `GET` is allowed.
4. An unknown path returns `404`.
5. Duplicate method/template registrations and equal-specificity overlapping
   routes for the same method fail while building the router.
6. Handler exceptions remain failed completion stages and are mapped to `500`
   only by the transport boundary.

### 5.2 JSON request and response helpers

Populate `http-json` with `JsonHttp` and `JsonHttpException`.

- Requests are decoded as UTF-8 into the existing immutable `JsonDataType`
  tree using a caller-selected `JsonParser`/`JsonLimits` profile.
- A non-empty body requires `application/json` or an `application/*+json`
  media type. Unsupported media types produce a typed `415` failure;
  malformed JSON produces a typed `400` failure.
- Empty bodies are rejected as `400` when a JSON value is requested.
- Response helpers serialize the value tree once, set
  `application/json; charset=utf-8`, and preserve exact UTF-8 byte length.
- A JSON error-response helper provides a small, deterministic
  `{ "error": ..., "message": ... }` envelope without imposing a domain
  object mapper.

Add the common status constants required by REST (`201`, `204`, `409`, and
`415`) to `http-api`.

### Step 5 exit criteria

- Unit tests cover literal precedence, named parameters, all method helpers,
  explicit `HEAD`, `GET` fallback, `404`, `405`/`Allow`, duplicates, and
  per-method ambiguity.
- JSON tests cover UTF-8, structured values, vendor JSON media types, malformed
  input, empty input, unsupported media types, parser limits, and error output.
- `http-routing` depends only on `http-api`; `http-json` depends only on
  `http-api` and `json`.

## Step 6 — JDK HTTP/WebSocket transport extraction

### 6.1 UI-independent WebSocket API

Populate `websocket-api` with:

- `WebSocketEndpoint`: request matching, handshake validation, subprotocol
  declaration, and connection listener creation.
- `WebSocketSession`: thread-safe text/binary sends, graceful close, forced
  abort, and open-state inspection.
- `WebSocketListener`: open, complete-message, pong, close, and error events.
- checked handshake/protocol exceptions and named RFC 6455 close-code constants.

The module depends on `http-api` because endpoint selection and handshake policy
operate on the same immutable `HttpRequest` seen by normal HTTP handlers. It has
no dependency on UI or the JDK socket implementation.

### 6.2 Generic JDK server

Move the following ownership from `ui-http` to `server-jdk`:

- HTTP/1.0 and HTTP/1.1 parsing and bounded request reads;
- response serialization and streamed response copying;
- listening socket, virtual-thread connection lifecycle, connection limit, and
  graceful stop;
- WebSocket handshake, frame validation, fragmentation, ping/pong, close
  handshake, message limits, and endpoint dispatch.

Expose `JdkWebServer`, configured with an `HttpApplication`, zero or more
`WebSocketEndpoint`s, a port, and a connection limit. A no-op-by-default
`JdkServerObserver` reports requests, failures, active WebSocket count, and
per-connection message/byte activity. This preserves observability while
keeping metrics policy outside the transport module.

Transport behavior remains deliberately bounded: one request per connection,
no keep-alive/pipelining, no chunked request decoding, no TLS, and a 256 KiB
request/message limit. Those constraints are documented API behavior, not
accidental UI-server behavior.

### 6.3 UI adapter migration

Refactor `ui-http.WebServer` into a UI-oriented facade:

- build `PageHttpHandler` and the RSP `WebSocketEndpoint`;
- delegate listening, parsing, response writing, and RFC 6455 mechanics to
  `JdkWebServer`;
- adapt `JdkServerObserver` callbacks to existing framework metrics;
- retain rendered-page storage, resumable-session lifecycle, static-resource
  handling, and the public UI server constructors.

Delete the parser/writer/socket/WebSocket transport implementations from
`ui-http`. `RspWebSocketEndpoint` uses only `websocket-api` contracts and named
close codes.

### Step 6 exit criteria

- `server-jdk` serves an `HttpApplication` without any UI dependency.
- A generic WebSocket endpoint passes handshake, text/binary, fragmentation,
  ping/pong, subprotocol, limit, close, and shutdown tests.
- Existing UI server socket, reconnect, metrics, and browser E2E tests pass
  through the extracted transport.
- `ui-http` contains no `java.net.ServerSocket`/`Socket` imports and no HTTP
  parser, response writer, WebSocket frame, upgrader, or connection class.
- The module-boundary audit enforces the new edges and forbidden imports.

## Implementation and verification sequence

1. Implement and unit-test `http-routing`.
2. Add REST statuses; implement and unit-test `http-json`.
3. Add `websocket-api` contracts and close-code vocabulary.
4. Implement `JdkWebServer` and move transport-focused tests to `server-jdk`.
5. Convert `ui-http.WebServer` and `RspWebSocketEndpoint` to adapters.
6. Update examples and reference documentation with a minimal REST application.
7. Run focused unit/socket tests, all example E2Es, the dependency-boundary
   audit, `git diff --check`, and a full reactor package.

## Implemented result

Steps 5 and 6 are implemented as specified:

- `http-routing` now provides immutable method-aware routing over the shared
  URL route table, with decoded route context, `HEAD` fallback, and deterministic
  `404`/`405` behavior.
- `http-json` now provides strict UTF-8 JSON request decoding, typed `400`/`415`
  failures, exact JSON responses, and deterministic error envelopes.
- `websocket-api` now owns the UI-independent endpoint, listener, session,
  protocol-error, handshake-error, and close-code vocabulary.
- `server-jdk` now owns HTTP parsing/writing, JDK sockets, connection limits,
  RFC 6455 framing and lifecycle, generic endpoint dispatch, and transport
  observation.
- `ui-http.WebServer` is now a UI facade over `JdkWebServer`; page rendering,
  local resume, RSP protocol, static resources, and metrics adaptation remain
  in `ui-http`.
- `rsp.app.rest.RestHello` demonstrates a REST-only application with no UI
  dependency at runtime.

The repository boundary audit enforces the resulting ownership and dependency
rules. The verification commands and their results are recorded in the final
implementation handoff.
