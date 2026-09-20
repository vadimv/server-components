# UI Core Module

The `ui-core` module is the server-side UI runtime.

Core provides:

- a Java DSL for HTML,
- stateful server-side UI components,
- virtual DOM rendering and diffing,
- live page sessions backed by browser events,
- browser command APIs such as DOM property reads and JavaScript evaluation.

Core is intentionally not the whole toolkit. The embedded HTTP server,
application compositions, authorization, schema, AI agent integration, and
examples live in sibling modules.

The embedded server class `rsp.http.WebServer` and initial-page HTTP adapter are
implemented in `ui-http`; transport-neutral HTTP values live in `http-api`.
Examples import them because they run complete apps, but `ui-core` itself is
HTTP-free and server-adapter neutral.

## Live Page Model

An interactive page is rendered on the server first, then kept alive through a WebSocket session:

```text
Browser                         Server
   |                               |
   | HTTP GET                      |
   |------------------------------>|
   |                               | Build component tree, render HTML
   | HTML response                 |
   |<------------------------------|
   |                               |
   | Open WebSocket                |
   |------------------------------>|
   |                               | Attach LivePageSession
   | Browser DOM event             |
   |------------------------------>|
   |                               | Run Java handler, update state
   | DOM patch / browser command   |
   |<------------------------------|
```

The framework keeps component state on the server. Event handlers run in Java.
When a handler updates state, the runtime renders the new virtual DOM, computes
the difference, and sends browser commands that update the real DOM.

Plain pages use the same DSL and component rendering, but do not load the
client script or open a live session.

## Smallest Useful Shape

A view is a function of state. A component view receives an
`IntentDispatcher<I>` and returns that state-to-HTML function. It can request
work from its owner, but it cannot update the owner's state cache directly.

```java
import rsp.component.ComponentView;
import rsp.component.definitions.LocalStateComponent;

import static rsp.dsl.Html.*;

record Counter(int value) {}
enum CounterIntent { INCREMENT }

final ComponentView<Counter, CounterIntent> view = intents -> state ->
        html(
                body(
                        h1("Current count: " + state.value()),
                        button(
                                on("click", event -> intents.dispatch(CounterIntent.INCREMENT)),
                                text("Increment"))
                )
        );

final var root = new LocalStateComponent<>(
        (_, _) -> new Counter(0), view,
        (state, intent) -> new Counter(state.value() + 1));
```

Read the type from left to right:

- `ComponentView<S, I>` receives an `IntentDispatcher<I>`.
- It returns a `View<S>`.
- `View<S>` receives the current immutable state snapshot and returns a DSL `Definition`.
- Dispatching an intent delegates state changes to the owning component.

To run a page in an embedded server, use the `ui-http` module:

```java
import rsp.http.WebServer;

final var server = new WebServer(8080)
        .page("/", (request, route) -> new LocalStateComponent<>((_, _) -> new Counter(0), view,
                (state, intent) -> new Counter(state.value() + 1)));
server.start();
server.join();
```

## HTML DSL

Import the DSL from `rsp.dsl.Html`:

```java
import static rsp.dsl.Html.*;
```

HTML tags are Java methods with matching names. Attributes use `attr(...)`.
Text can be passed directly to common tag overloads or created with
`text(...)`.

```java
import rsp.component.View;

record PageState(String text) {}

View<PageState> page = state ->
        html(
                body(
                        h1("This is a heading"),
                        div(attr("class", "par"),
                                p("This is a paragraph"),
                                p(state.text()))
                )
        );
```

Use `of(...)` to insert a sequence of definitions and `when(...)` for
conditional markup:

```java
state -> ul(
        of(state.items().stream().map(item -> li(item.name()))),
        when(state.showSummary(), () -> li("Summary"))
)
```

`of(Supplier<Definition>)` is useful when a block of ordinary Java is clearer
than a nested expression:

```java
state -> of(() -> {
    if (state.showInfo()) {
        return p(state.info());
    }
    return p("none");
})
```

There is also an `of(CompletableFuture<? extends Definition>)` overload. It
waits for the future with `join()`, so use it only when blocking the render is
acceptable or the future is already complete.

## HTML Documents And Page Modes

The HTML DSL describes markup only:

```java
html(
        head(title("Admin")),
        body(...)
)
```

`head(...)` is a normal HTML element. It does not select a transport or inject
scripts. The `ui-http` adapter explicitly selects live or static rendering:

```java
PageResult live = PageResult.live(component);
PageResult detached = PageResult.staticHtml(component);
```

Only the live mode injects page configuration and the WebSocket client.

## Components

`Component<S, I>` is the base class for reusable UI definitions. A component
instance is the definition/controller object; the mounted runtime object is a
`ComponentSegment`, and that segment owns the current state.

Prefer immutable state records:

```java
record CounterState(int value) {}
enum CounterIntent { INCREMENT }
```

A custom component supplies initial state and a component view:

```java
import rsp.component.ComponentStateSupplier;
import rsp.component.ComponentView;
import rsp.component.definitions.Component;

import static rsp.dsl.Html.*;

final class CounterComponent extends Component<CounterState, CounterIntent> {
    @Override
    public ComponentStateSupplier<CounterState> initStateSupplier() {
        return (key, context) -> new CounterState(0);
    }

    @Override
    public ComponentView<CounterState, CounterIntent> componentView() {
        return intents -> state ->
                div(
                        span("Count: " + state.value()),
                        button(
                                on("click", event -> intents.dispatch(CounterIntent.INCREMENT)),
                                text("+"))
                );
    }

    @Override
    protected void onIntent(CounterIntent intent, CounterState state,
                            StateUpdater<CounterState> stateUpdater) {
        if (intent == CounterIntent.INCREMENT) {
            stateUpdater.setState(new CounterState(state.value() + 1));
        }
    }
}
```

Useful built-in definitions:

- `InitialStateComponent<S, I>`: fixed initial state plus an intent-only view.
- `LocalStateComponent<S, I>`: fixed local cache plus a reducer for its intents.
- `StatelessComponent`: a component without durable state.
- `StoredStateComponent<S>`: state loaded from and saved to a state store.
- `AddressBarSyncComponent` and `ContextStateComponent`: low-level building
  blocks used by higher-level composition modules.

The lifecycle callbacks on `Component` are available when a component needs to
coordinate with external resources:

- `onBeforeUpdated(...)` can veto a state update.
- `onAfterRendered(...)` is the usual place to register component or window
  event subscriptions.
- `onMounted(...)`, `onUpdated(...)`, and `onUnmounted(...)` are lifecycle
  notifications.

Keep user-visible state in the state snapshot, not in mutable component fields.
Component fields are best used for immutable collaborators such as services,
formatters, configuration, or loggers.

## Context And Component Events

`ComponentContext` is an immutable typed context passed down the component tree.
Components can enrich the context for descendants by overriding
`subComponentsContext()`.

`Lookup` and `ContextLookup` combine three common capabilities:

- reading values from `ComponentContext`,
- publishing component events through the command queue,
- subscribing to component events through the current component segment.

Typed event names are represented by `EventKey`:

```java
import rsp.component.EventKey;

static final EventKey.VoidKey SAVED = new EventKey.VoidKey("saved");
static final EventKey.SimpleKey<String> MESSAGE =
        new EventKey.SimpleKey<>("message", String.class);
```

Render-created DOM handlers dispatch component intents. Component callbacks and
intent handlers can publish typed events through `StateUpdater` or `Lookup`:

```java
button(
        on("click", event -> intents.dispatch(EditorIntent.SAVE)),
        text("Save"))
```

Subscriptions are commonly registered from `onAfterRendered(...)`:

```java
record State(boolean saved) {
    State markSaved() {
        return new State(true);
    }
}

@Override
public void onAfterRendered(State state,
                            Subscriber subscriber,
                            CommandsEnqueue commands,
                            StateUpdater<State> stateUpdater) {
    subscriber.addEventHandler(SAVED, () ->
            stateUpdater.applyStateTransformation(State::markSaved));
}
```

Higher-level modules such as `compositions` build routing, blocks, layouts,
and admin workflows on top of these lower-level primitives.

## DOM Events

Attach browser event handlers with `on(eventType, handler)`:

```java
button(
        on("click", event -> intents.dispatch(CounterIntent.INCREMENT)),
        text("Increment"))
```

Use the overload with `preventDefault` for forms and links:

```java
form(
        on("submit", true, event -> {
            System.out.println(event.eventObject());
        }),
        input(attr("type", "text"), attr("name", "title")),
        button(attr("type", "submit"), text("Submit"))
)
```

`event.eventObject()` returns the browser event payload as a `JsonDataType.Object`.

Custom DOM events can be dispatched from an event handler. They bubble through
ancestor DOM paths and can be handled by matching `on(...)` definitions:

```java
import rsp.page.events.CustomEvent;
import rsp.util.json.JsonDataType;

div(
        on("custom-event", event ->
                System.out.println(event.eventObject())),
        button(
                on("click", event ->
                        event.dispatchEvent(new CustomEvent(
                                "custom-event",
                                JsonDataType.Object.EMPTY.put(
                                        "key",
                                        new JsonDataType.String("value"))))),
                text("Dispatch"))
)
```

Window events are registered through `window()`:

```java
html(
        window().on("click", event ->
                System.out.println("window clicked")),
        body(...)
)
```

## DOM References

Use element references when a server-side handler needs to read or write a
browser-side property, such as an input value.

```java
import rsp.ref.ElementRef;
import rsp.util.json.JsonDataType;

import static rsp.dsl.Html.*;

final ElementRef titleInput = createElementRef();

form(
        input(
                ref(titleInput),
                attr("type", "text"),
                attr("name", "title")),
        button(
                attr("type", "button"),
                on("click", event -> {
                    var props = event.propertiesByRef(titleInput);
                    props.get("value").thenAccept(value -> {
                        if (value instanceof JsonDataType.String title) {
                            System.out.println(title.value());
                            props.set("value", "");
                        }
                    });
                }),
                text("Read"))
)
```

`propertiesByRef(...)` returns a `PropertiesHandle`. Its `get(...)` method
returns a `CompletableFuture<JsonDataType>` because the value is read from the
browser asynchronously. Its `set(...)` method sends a DOM property update to the browser.

## Client Commands

An `EventContext` can send commands back to the browser:

```java
button(
        on("click", event ->
                event.evalJs("1 + 1").thenAccept(result ->
                        System.out.println("Result: " + result))),
        text("Calculate"))
```

Useful methods:

- `evalJs(String)`: evaluates JavaScript in the browser and returns
  `CompletableFuture<JsonDataType>`.
- `evalJs(String, Consumer<JsonDataType>)`: callback convenience overload.
- `setHref(String)`: changes the browser URL.
- `dispatchEvent(CustomEvent)`: dispatches a custom DOM event from the current
  event target.

`evalJs(...)` is useful when code needs direct browser-side interop, for
example with platform APIs or client-side libraries. It is not the primary
state-management tool: prefer DSL state and DOM refs when the same behavior can
stay naturally expressed in Java, and use JavaScript evaluation when its
coupling, async result handling, and browser-only execution model are the right
tradeoff.

## Initial HTTP Response Metadata

`HtmlDocument` contains only markup. At the `ui-http` boundary, a `PageResult`
can attach initial response metadata without coupling components to HTTP:

```java
PageResult.staticHtml(notFoundComponent)
        .status(HttpStatus.NOT_FOUND)
        .header("Cache-Control", "no-store");
```

For redirects:

```java
PageResult.redirect("/login");
```

The transport-neutral request and response types live in `http-api` under
`rsp.http`. A `PageApplication` receives `HttpRequest` before rendering and
returns `HttpResult`, which is how initial GUI logic reads request data, adds
headers, sets cookies, returns a direct `HttpResponse`, or redirects. Once the
live page response has been sent, browser navigation uses component commands
such as `setHref`; it cannot modify that completed HTTP response.

Use the UI-facing `Router` when pages belong to specific server-side paths and
methods. It can declare page and ordinary HTTP endpoints together:

```java
Router routes = HttpRouter.builder()
        .get("/orders/{id}", (request, route) ->
                PageResult.live(orderPage(route.requiredParameter("id"))))
        .post("/orders/{id}", (request, route) ->
                PageResult.staticHtml(orderSubmitted(request, route.requiredParameter("id"))))
        .get("/api/health", (request, route) ->
                HttpResponse.ok().text("ok").build())
        .build();

new WebServer(8080).routes(routes);
```

All declarations share one method-aware graph and one ambiguity check. Attach
a `PageApplication` with `WebServer.pageApplication(...)` when unknown server
paths should intentionally fall through to page selection, as with client-side
UI routing. `WebServer.page(template, handler)` registers an explicit live
`GET` route; repeated calls are additive.

## Static Resources

Static resource configuration lives in `ui-http`:

```java
import rsp.http.StaticResources;

import java.io.File;

final var staticResources =
        new StaticResources(new File("src/main/resources/public"), "/res/");
```

Pass it to `WebServer`:

```java
new WebServer(8080)
        .page("/", (request, route) -> app.apply(request))
        .staticResources(staticResources);
```

Use a trailing slash for static resource context paths such as `"/res/"`.

`SslConfiguration` currently lives in `ui-http`, but the embedded server does
not implement TLS and rejects an SSL configuration at
startup. See the [HTTP server reference](../reference/http-server.md) for the
implemented protocol and deployment limits.

## Logging And Diagnostics

Server-side logging uses `System.Logger`.

For detailed client protocol logging, open the browser console on a live page
and run:

```javascript
RSP.setProtocolDebugEnabled(true)
```
