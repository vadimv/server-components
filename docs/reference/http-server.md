# HTTP Server

Status: current as of `system/http` in this repository

The `http` artifact provides the embedded `rsp.http.WebServer`. It uses JDK
sockets and virtual threads, serves initial RSP pages and static files, and
binds live page sessions over WebSocket. It has no third-party runtime
dependency.

## Start And Stop

```java
WebServer server = new WebServer(8080, request -> rootComponent(request));
server.start();
server.join();
```

`start()` binds the socket and returns. `join()` blocks until the acceptor stops.
Call `stop()` during application shutdown. It stops accepting connections,
clears pages waiting for a WebSocket, sends close code `1001` to live sockets,
waits for a bounded close handshake, and then force-closes remaining sockets.

Use port `0` in integration tests. After `start()`, `port()` returns the actual
bound port:

```java
WebServer server = new WebServer(0, request -> rootComponent(request));
server.start();
int port = server.port();
```

The default connection limit is `WebServer.DEFAULT_CONNECTION_LIMIT` (`50`).
The advanced constructor accepts a positive custom limit and an `EventLoop`
supplier for deterministic tests.

## Static Resources

Mount one directory at a context path ending in `/`:

```java
StaticResources resources =
        new StaticResources(new File("src/main/resources/public"), "/res/");
WebServer server = new WebServer(8080, app, resources);
```

The bundled browser client is served automatically from
`/static/js-client.min.js`.

## HTTP Behavior

The current server supports HTTP/1.0 and HTTP/1.1 request parsing for:

- `GET`, `HEAD`, and `POST`;
- query parameters, headers, and cookies;
- `application/x-www-form-urlencoded` request bodies merged into the request's
  query parameters;
- repeated response headers and `InputStream` response bodies;
- response status and headers set on `HtmlDocument`.

Each non-WebSocket response closes its connection. Keep-alive, pipelining,
chunked request or response bodies, multipart forms, and a general request-body
API are not implemented.

Current parser limits are fixed in the implementation:

| Limit | Value |
| --- | ---: |
| Request line | 8 KiB |
| All request headers | 16 KiB |
| Header count | 100 |
| Form body | 256 KiB |
| Header read timeout | 5 seconds |
| Body read timeout | 10 seconds |

Malformed or oversized requests return the corresponding `400`, `408`, `413`,
`414`, or `431` response. Known but unsupported HTTP methods return `405`;
unknown method tokens return `501`.

## WebSocket Behavior

Live pages connect to:

```text
/bridge/web-socket/{deviceId}/{sessionId}
```

The server validates RFC 6455 upgrade headers, masking, control frames,
fragmentation, close frames, and UTF-8 text. It responds to ping frames and caps
an assembled inbound message at 256 KiB. The RSP application protocol currently
uses WebSocket text messages; binary RSP messages are rejected as unsupported.

The JavaScript client's long-polling routes are not implemented by this server.

## TLS And Deployment Limits

TLS is not implemented in `system/http`. Although compatibility constructors
accept `SslConfiguration`, `start()` throws `UnsupportedOperationException` when
one is supplied. Do not use the TLS constructor in current applications.

HTTP/2, SSE, streaming/chunked responses, and a generic WebSocket endpoint API
are also outside the current public server. TLS termination and reverse-proxy
behavior must be provided and validated by the deployment environment.

## Tests

Run the server's parser, socket, and WebSocket tests with:

```bash
mvn -pl system/http -am test
```
