# HTTP Server

Status: current as of `system/server-socket` and `system/ui-http` in this repository

The `server-socket` artifact owns the embedded socket and virtual-thread
transport. `rsp.server.socket.SocketWebServer` accepts any transport-neutral
`HttpApplication` plus zero or more `WebSocketEndpoint`s. The `ui-http`
artifact provides `rsp.http.WebServer`, a UI facade which supplies page
rendering, static-resource, resumable-session, and RSP WebSocket adapters to
that generic transport.

## Start And Stop

```java
WebServer server = WebServer.pages(8080, Pages.live(request -> rootComponent(request)));
server.start();
server.join();
```

`start()` binds the socket and returns. `join()` blocks until the acceptor stops.
Call `stop()` during application shutdown. It stops accepting connections,
clears pages waiting for a WebSocket, sends close code `1001` to live sockets,
releases local live-page sessions, waits for a bounded close handshake, and then
force-closes remaining sockets.

Use port `0` in integration tests. After `start()`, `port()` returns the actual
bound port:

```java
WebServer server = WebServer.pages(0, Pages.live(request -> rootComponent(request)));
server.start();
int port = server.port();
```

The default connection limit is `WebServer.DEFAULT_CONNECTION_LIMIT` (`50`).
Use `WebServer.builder(...)` to set a positive custom limit, an `EventLoop`
supplier for deterministic tests, metrics, static resources, or generic HTTP
routes.

REST-only applications can use the transport directly and do not need a UI
dependency:

```java
HttpRouter application = HttpRouter.builder()
        .get("/api/hello/{name}", HttpRouteHandler.sync((request, route) ->
                HttpResponse.ok().text("Hello, " + route.requiredParameter("name")).build()))
        .build();

SocketWebServer server = new SocketWebServer(8080, application);
server.start();
server.join();
```

For a JSON API, `http-rest` joins routing and JSON without making either
lower-level module depend on the other:

```java
record Message(String value) {}

JsonCodec<Message> messages = JsonCodec.of(
        json -> new Message(Json.requireObject(json).requiredString("message")),
        message -> Json.object().put("message", message.value()));

HttpRouter application = HttpRouter.builder()
        .post("/api/messages", RestRouteHandler.jsonSync(messages,
                (request, route, message) ->
                        JsonHttp.response(HttpStatus.CREATED, message, messages)))
        .build();
```

`RestRouteHandler.json(...)` and `jsonSync(...)` validate the media type, parse
the body, and decode it before calling the endpoint. Empty, malformed, or
shape-invalid JSON produces the standard `invalid_json` envelope; an unsupported
media type produces the same envelope with status `415`. Throw a
`RestException` for an expected domain failure with an explicit status, stable
error code, and public message. Unexpected exceptions remain failed completion
stages and reach the transport's generic `500` boundary, so internal exception
details are not exposed.

The lower-level APIs remain available separately. `JsonHttp.read(...)` and
`response(...)` accept either the immutable JSON tree or a `JsonCodec<T>`, while
`HttpRouteHandler` continues to support non-JSON handlers with no REST error
policy.

`SocketWebServer` has equivalent `start()`, `join()`, `stop()`, ephemeral-port,
and connection-limit behavior. Its most explicit constructor also accepts
WebSocket endpoints, a WebSocket read timeout, and a `SocketServerObserver`.

## Cross-cutting HTTP policy

Use `HttpMiddleware.pipeline(...)` around any `HttpApplication` for shared
request/response policy. The first middleware is the outer request boundary;
responses return through the list in reverse order:

```java
CorsPolicy cors = CorsPolicy.builder()
        .allowOrigin("https://client.example")
        .allowMethods(HttpMethod.GET, HttpMethod.POST)
        .allowHeaders("Content-Type", "Authorization")
        .allowCredentials()
        .build();

HttpApplication production = HttpMiddleware.pipeline(application,
        new RequestIdMiddleware(),
        AccessLogMiddleware.systemLogger(System.getLogger("http.access")),
        SecurityHeadersMiddleware.defaults(),
        new CorsMiddleware(cors),
        ServerErrorMiddleware.systemLogger(System.getLogger("http.errors")));
```

Request IDs are bounded and safe for logs. Access events contain the raw path,
not the query, and report only a failure class rather than its message. CORS is
deny-by-default and valid preflights bypass the application. The default
security-header middleware is opt-in and deliberately excludes deployment-
specific CSP and HSTS choices. `ServerErrorMiddleware` creates a body-free
`500` before the transport boundary and reports the failure class without its
message; putting it last lets outer middleware decorate that response.

`WebServer.Builder.middleware(...)` applies middleware around application
routes, framework assets, static resources, and the page fallback.

## OpenAPI route metadata

Exact router registrations may carry format-neutral metadata. `http-openapi`
provides one such metadata type and an immutable schema DSL:

```java
OpenApiOperation getMessage = OpenApiOperation.builder()
        .operationId("getMessage")
        .pathParameter("id", OpenApiSchema.string())
        .jsonResponse(200, "Message", OpenApiSchema.ref("Message"))
        .response(404, "Not found")
        .build();

HttpRouter api = HttpRouter.builder()
        .get("/api/messages/{id}", handler, getMessage)
        .build();

OpenApiDocument document = OpenApiDocument.builder(
                new OpenApiInfo("Messages", "1.0.0"), api)
        .componentSchema("Message", OpenApiSchema.object()
                .requiredProperty("message", OpenApiSchema.string()))
        .build();

HttpRouter application = HttpRouter.builder()
        .include(api)
        .get("/openapi.json", document.handler())
        .build();
```

Undocumented routes and literal-prefix handlers are omitted. Missing template
parameters are emitted as required strings; contradictory path parameters,
duplicate operation IDs, and documented `CONNECT` methods fail when the
document is built.

Both `HttpApplication` and `PageApplication` can carry an
`ApplicationLifecycle`. The socket server starts an HTTP application before it
accepts requests and stops it after connections drain. The UI facade closes
live page sessions before stopping its page application. Use
`HttpApplication.withLifecycle(...)` or `PageApplication.withLifecycle(...)`
when adapting a handler lambda; otherwise the lifecycle would be lost.

## Combine HTTP Routes And UI Pages

The UI-facing `rsp.http.Router` is one immutable graph for ordinary HTTP
responses and rendered pages. Build its `rsp.http.HttpRouter` implementation
with direct synchronous lambdas; the lambda result determines whether the
route is an HTTP endpoint or a page endpoint:

```java
Router routes = HttpRouter.builder()
        .get("/items/{id}", (request, route) ->
                Pages.staticHtml(itemPage(route.requiredParameter("id"))))
        .post("/items/{id}", (request, route) ->
                Pages.staticHtml(updatedItemPage(request, route.requiredParameter("id"))))
        .get("/api/items/{id}", (request, route) ->
                HttpResponse.ok()
                        .text("item=" + route.requiredParameter("id"))
                        .build())
        .build();

WebServer server = WebServer.builder(8080)
        .routes(routes)
        .build();
```

Asynchronous `HttpRouteHandler` and `RestRouteHandler` values use the same
builder's `getAsync(...)`, `postAsync(...)`, and corresponding method helpers.
The distinct names avoid ambiguous Java lambda overloads while leaving the
common synchronous form uncluttered. Route metadata remains attached to HTTP
routes, so a mixed `Router` can also be passed to `OpenApiDocument`;
undocumented page routes are omitted from the generated API document.

Generic and page routes are combined before matching, so they may handle
different methods on the same path. A method owned by neither returns `405`
with the complete `Allow` value. An unknown path falls through to framework
assets and mounted static resources, then returns `404` for a route-only
server. Route sets compose with `HttpRouter.Builder.include(...)` or repeated
`WebServer.Builder.routes(...)` calls. UI-independent libraries may continue
to expose `rsp.http.routing.HttpRouter`; the UI-facing builder and `WebServer`
accept those route sets without making the lower routing module depend on UI
components.

`WebServer.builder(port, pageApplication)` remains the terminal-page form for
client-side routing and other applications that intentionally select a page
for otherwise unknown paths. Its `PageApplication` runs after exact generic
and page routes, framework assets, and static resources.

`HttpRouter` also supports literal prefix handlers such as
`getPrefix("/assets", ...)`. Exact route templates win over prefix handlers;
among prefixes, the longest matching prefix wins. `HttpPrefixContext` exposes
both the selected prefix and its relative remainder. A router-level fallback
is invoked only for paths unknown to every method, and owns the fallback
application lifecycle.

## Runtime Metrics

Pass one process-wide `Metrics` sink to the metrics-aware constructor to record
HTTP requests and failures, active WebSockets, retained page sessions, and
component-segment lifecycle events. Existing constructors use a no-op sink.
The optional `metrics-runtime` extension supplies the production registry and
a read-only local JMX mirror; see [runtime metrics and local JMX](../guides/runtime-metrics.md).

## Local Session Resume

A live page is retained in the server process when its WebSocket disconnects.
The bundled browser client reconnects with the same device and session IDs,
reports the last server message it applied, and receives any later messages in
order. The component tree and event loop are not remounted during a successful
resume.

The defaults retain a detached page for 60 seconds and bound its unacknowledged
message journal to 4,096 messages or 4 MiB, whichever is reached first. The
most explicit constructor accepts different bounds:

```java
var resume = new LocalSessionResumeConfig(
        Duration.ofMinutes(2),
        8_192,
        8L * 1024L * 1024L);

var server = WebServer.builder(8080, pageApplication)
        .connectionLimit(WebServer.DEFAULT_CONNECTION_LIMIT)
        .eventLoops(DefaultEventLoop::new)
        .localSessionResume(resume)
        .metrics(Metrics.noop())
        .build();
```

Expiry is measured from a confirmed detachment. Failed reconnect attempts do
not extend it; a completed resume cancels it. While connected, the journal is a
sliding window: reaching a bound discards its oldest delivered copies without
interrupting the page. Resume remains possible when the browser reports that it
already applied that discarded prefix. A detached journal overflow, explicit
browser termination, protocol failure, or server shutdown releases the page
immediately. The browser blocks new UI interaction while detached rather than
queueing potentially stale events.

Resume is deliberately process-local. With more than one application node, the
initial HTTP request, first WebSocket, and subsequent WebSocket reconnects must
be routed to the same node. A reconnect routed elsewhere causes a page reload.

## Static Resources

Mount one directory at a context path ending in `/`:

```java
StaticResources resources =
        new StaticResources(new File("src/main/resources/public"), "/res/");
WebServer server = WebServer.builder(8080, Pages.live(app))
        .staticResources(resources)
        .build();
```

The bundled browser client is served automatically from
`/static/js-client.min.js`.

## Initial Page Results

The initial request is handled before a component is rendered. This is the
appropriate place to inspect HTTP data and choose a page response:

```java
PageApplication pages = request -> {
    if (request.header("Authorization") == null) {
        return Pages.redirect("/login");
    }
    return Pages.live(rootComponent(request.relativeUrl()))
            .header("X-Frame-Options", "DENY");
};

WebServer server = WebServer.pages(8080, pages);
```

Use `Pages.staticHtml(component)` for detached server-rendered HTML and
`Pages.response(httpResponse)` when no UI rendering is required. `HtmlDocument`
does not contain HTTP status, header, cookie, or redirect state.

The supplied `ui-http-auth` providers apply this boundary consistently and
pass an immutable `Authentication` value to the page callback before component
creation. See [Authentication](../concepts/authentication.md).

## HTTP Behavior

The current server supports HTTP/1.0 and HTTP/1.1 request parsing for:

- all standard `HttpMethod` values, with method policy left to the application;
- query parameters, headers, and cookies;
- `application/x-www-form-urlencoded` request bodies merged into the request's
  query parameters;
- bounded byte-oriented request bodies;
- repeated response headers and repeatable or streamed response bodies;
- status, headers, cookies, redirects, and direct responses selected by
  `PageResult` before UI rendering.

Each non-WebSocket response closes its connection. Keep-alive, pipelining,
chunked request or response bodies, multipart forms, and a general request-body
decoder registry are not implemented. `HttpApplication` is the UI-neutral
application contract and can be bound directly to `SocketWebServer`. `HttpRouter`
adds composable exact and literal-prefix routing, `HEAD` fallback, terminal
application fallback, and `404`/`405` behavior;
`JsonHttp` adds strict UTF-8 JSON request and response helpers; and
`RestRouteHandler` adds codec-aware body handling plus explicit JSON failure
mapping at the route boundary.

Current parser limits are fixed in the implementation:

| Limit | Value |
| --- | ---: |
| Request line | 8 KiB |
| All request headers | 16 KiB |
| Header count | 100 |
| Request body | 256 KiB |
| Header read timeout | 5 seconds |
| Body read timeout | 10 seconds |

Malformed or oversized requests return the corresponding `400`, `408`, `413`,
`414`, or `431` response. Unknown method tokens return `501`.

## WebSocket Behavior

Live pages connect to:

```text
/bridge/web-socket/{deviceId}/{sessionId}
```

The server validates RFC 6455 upgrade headers, masking, control frames,
fragmentation, close frames, and UTF-8 text. It responds to ping frames and caps
an assembled inbound message at 256 KiB. The RSP application protocol currently
uses WebSocket text messages; binary RSP messages are rejected as unsupported.

Commands produced by one component update retain their order and are transported
in general-purpose batches. Batches are split at 128 commands or 64 KiB; a
single command larger than the byte limit is sent alone. Every embedded command
still has its own contiguous sequence number, so reconnect can replay only the
unapplied suffix of a batch. The browser acknowledges the highest successfully
applied sequence cumulatively, flushing after 256 commands or 50 ms (and at
resume/disconnect boundaries). This avoids a WebSocket frame and acknowledgement
round trip for every event-listener removal or other small command.

Custom endpoints implement the contracts from `websocket-api` and are passed
to `SocketWebServer`. Endpoint matching and handshake policy therefore remain
independent of UI code. The JavaScript client's long-polling routes are not
implemented by this server.

## TLS And Deployment Limits

TLS is not implemented by `server-socket`. Although `ui-http` constructors accept
`SslConfiguration`, `start()` throws `UnsupportedOperationException` when one
is supplied. Do not use the TLS constructor in current applications.

HTTP/2, SSE, keep-alive, pipelining, and chunked request or response bodies are
also outside the current server. TLS termination and reverse-proxy behavior
must be provided and validated by the deployment environment.

## Tests

Run the generic transport and UI-adapter tests with:

```bash
mvn -pl system/server-socket,system/ui-http -am test
```
