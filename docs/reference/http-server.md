# HTTP Server

Status: current as of `system/server-jdk` and `system/ui-http` in this repository

The `server-jdk` artifact owns the embedded JDK-socket and virtual-thread
transport. `rsp.server.jdk.JdkWebServer` accepts any transport-neutral
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
The advanced constructor accepts a positive custom limit and an `EventLoop`
supplier for deterministic tests.

REST-only applications can use the transport directly and do not need a UI
dependency:

```java
HttpRouter application = HttpRouter.builder()
        .get("/api/hello/{name}", HttpRouteHandler.sync((request, route) ->
                HttpResponse.ok().text("Hello, " + route.requiredParameter("name")).build()))
        .build();

JdkWebServer server = new JdkWebServer(8080, application);
server.start();
server.join();
```

`JdkWebServer` has equivalent `start()`, `join()`, `stop()`, ephemeral-port,
and connection-limit behavior. Its most explicit constructor also accepts
WebSocket endpoints, a WebSocket read timeout, and a `JdkServerObserver`.

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

var server = new WebServer(
        8080,
        pageApplication,
        Optional.empty(),
        Optional.empty(),
        WebServer.DEFAULT_CONNECTION_LIMIT,
        DefaultEventLoop::new,
        resume,
        Metrics.noop());
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
WebServer server = WebServer.pages(8080, Pages.live(app), resources);
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
application contract and can be bound directly to `JdkWebServer`. `HttpRouter`
adds method-aware routing, including `HEAD` fallback and `404`/`405` behavior;
`JsonHttp` adds strict UTF-8 JSON request and response helpers.

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
to `JdkWebServer`. Endpoint matching and handshake policy therefore remain
independent of UI code. The JavaScript client's long-polling routes are not
implemented by this server.

## TLS And Deployment Limits

TLS is not implemented by `server-jdk`. Although `ui-http` constructors accept
`SslConfiguration`, `start()` throws `UnsupportedOperationException` when one
is supplied. Do not use the TLS constructor in current applications.

HTTP/2, SSE, keep-alive, pipelining, and chunked request or response bodies are
also outside the current server. TLS termination and reverse-proxy behavior
must be provided and validated by the deployment environment.

## Tests

Run the generic transport and UI-adapter tests with:

```bash
mvn -pl system/server-jdk,system/ui-http -am test
```
