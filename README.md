# Server Components
[![javadoc](https://javadoc.io/badge2/io.github.vadimv/rsp/javadoc.svg)](https://javadoc.io/doc/io.github.vadimv/rsp)

* [UI server components](#ui-components)
* [Routing](#routing)
* [HTML markup Java DSL](#html-markup-java-dsl)
* [DOM events](#dom-events)
* [SPAs, plain pages head tag](#spas-plain-pages-and-the-head-tag)
* [Page HTTP status code and HTTP headers](#page-http-status-code-and-http-headers)
* [Evaluating code on the client-side](#evaluating-js-code-on-client-side)
* [Web server's configuration](#web-servers-configuration)
* [Logging](#logging)

Server Components is a Java web Server Side Rendering (SSR) framework for building responsive UIs with minimal dependencies.

### UI server components

Web UIs are composed of components. Components may contain HTML DSL and/or other components. Every web page has its root component.
Every component is associated with an immutable state snapshot which is set during an initialization and can be updated a result of this browser's page or an external events.
A change of a component's state results with re-rendering of the relevant component and all it children components.
All components on a page share a session objects basket which can be used to exchange information between components.

Every component has its view which may contain conditional rendering logic and events' handlers.
A simple view is a pure function from an input state to a DOM tree definition.

Besides basic components types a custom component could be created by extending ``StatefulComponentDefinition<S>`` class or some of the basic types.


### HTML markup Java DSL

ASC core provides a Java internal domain-specific language to define HTML templates as a composition of functions.

For example, the HTML fragment:

```html
<!DOCTYPE html>
<html>    
    <body>
        <h1>This is a heading</h1>
        <div class="par">
            <p>This is a paragraph</p>
            <p>Some dynamic text</p>
        </div>
    </body>
</html> 
```

to be represented in the Java DSL as the code fragment below:

```java
    import static rsp.html.HtmlDsl.*;
    ...
    final View<State> view = state -> html(
                                          body(
                                               h1("This is a heading"),
                                               div(attr("class", "par"), 
                                                   p("This is a paragraph"),
                                                   p(state.text)) // adds a paragraph with a text from the state object
                                              ) 
                                        );
```
where:
- HTML tags are the ``rsp.html.HtmlDsl`` class' methods with same names, e.g. ``<div></div>``  translates to ``div()``
- HTML attributes are the ``rsp.html.HtmlDsl.attr(name, value)`` function, e.g. ``class="par"`` translates to ``attr("class", "par")``
- Text nodes are represented either by tags methods' String parameter or by the ``rsp.html.HtmlDsl.text(string)`` wrapper

The  ``of()`` DSL function takes a ``Stream<T>`` of objects, e.g. a sequence of tags, or a table rows:
```java
    import static rsp.html.HtmlDsl.*;
    ...
    state -> ul(of(state.items.stream().map(item -> li(item.name))))
```

An overloaded variant of ``of()`` accepts a ``CompletableFuture<S>``:
```java
    final Function<Long, CompletableFuture<String>> lookupService = userDetailsByIdService(); 
    ...
     // consider that at this moment we know the current user's Id
    state -> stateUpdate -> div(of(lookupService.apply(state.user.id).map(str -> text(str))))
```

another overloaded ``of()`` function takes a ``Supplier<S>`` as its argument and allows inserting code fragments.

```java
    import static rsp.html.HtmlDsl.*;
    ...
    state -> of(() -> {
                     if (state.showInfo) {
                         return p(state.info);
                     } else {
                         return p("none");
                     }       
                 })
```

The ``when()`` DSL function conditionally renders (or not) an element:

```java
    state -> when(state.showLabel, span("This is a label"))
```

### DOM events

To respond to browser events, register a DOM event handler by adding an ``on(eventType, handler)`` on an HTML element:

```java
    final ComponentView<State> view = state -> stateUpdate -> 
                                      a("#", "Click me", on("click",
                                                            ctx-> { 
                                                              System.out.println("Clicked " + state.counter() + " times");
                                                              stateUpdate.setState(new State(s.counter() + 1));
                                                            }));

    record State(int counter) {}
```

Some types of browser events, like a mouse move, may fire a lot of events' invocations.
Sending all these notifications over the network and processing them on the server side may cause the system's overload.

To filter the events use the following event object methods:

- ``throttle(int timeFrameMs)``
- ``debounce(int waitMs, boolean immediate)``

```java
    window().on("scroll", ctx -> {
            System.out.println("A throtteld page scroll event");
            }).throttle(500)
```

The ``ctx.eventObject()`` method provides its event's object as a JSON data structure:

```java
    form(on("submit", ctx -> {
            // Prints the submitted form's input field value
            System.out.println(ctx.eventObject());
         }),
        input(attr("type", "text"), attr("name", "val")),
        input(attr("type", "button"), attr("value", "Submit"))
    )
```

To send a custom event to parent components use the ``ctx.dispatchEvent()`` method:

```java

    on("custom-event",
       ctx -> {
            System.out.println("Custom event object: " + ctx.eventObject());
        })
    ...
        on("click",
            ctx -> ctx.dispatchEvent(new CustomEvent("custom-event",
                                                     JsonDataType.Object.EMPTY.put("key",
                                                                                   new JsonDataType.String("value")))))

```
A custom event is bubbled from a child element and can be handled by its ancestor's event handler.

The ``window().on(eventType, handler)`` DSL function registers an event handler on the browser's window object:

```java
    html(window().on("click", ctx -> {
            System.out.println("window clicked");
        })
        ...
    )
```

### Single Page Applications, plain pages and the head tag

Thre are two types of web pages:
- server-side single-page applications (SPAs), written in Java, e.g. for an admin UI
- plain server-rendered detached HTML pages

The page's ``<head>`` tag DSL determines if this page is an interactive Single-Page-Application or a plain HTML page.

The ``head(...)`` or ``head(PageType.SPA, ...)`` function creates an HTML page ``<head>`` tag for an SPA.
If the ``head()`` is not present in the page's markup, the simple SPA-type header is added automatically.
This type of head injects a script, which establishes a WebSocket connection between the browser's page and the server
and enables reacting to the browser events.

``head(HeadType.PLAIN, ...)`` renders the markup with the ``<head>`` tag without injecting of init script
to establish a connection with server and enable server side events handling for SPA.
This results in rendering of a plain detached HTML page.

### Page HTTP status code and HTTP headers

- ``statusCode()`` method sets result response HTTP status code.
- ``addHeaders()`` method adds response headers.

For example:

```java
    __ -> html(   
                  head(HeadType.PLAIN, title("404 page not found")),
                  body(
                       div(
                           p("404 page not found")
                      ) 
                    )
                ).statusCode(404);
```

### HTTP request routing

A web page's components tree HTML generation consists of two phases:
- a routing function maps an incoming HTTP request to the root component state
- the root component and its descendants render this state to HTML markup

To define a routing of an incoming request, create a ``Routing`` and provide it as a parameter:

```java
    import static rsp.html.RoutingDsl.*; 

    final var db = new Database();
    
    Routing<HttpRequest, State> routes() {
        return routing(concat(get("/articles", req -> db.getArticles()),
                              get("/articles/:id", (__, id) -> db.getArticle(id)),
                              get("/users/:id", (__, id) ->db.getUser(id))),
                              post("/users/:id(^\\d+$)", (req, id) -> { db.setUser(id, req.queryParam("name"));
                                                                        return State.userWriteSuccess(); },
                       State.NOT_FOUND_404));
    }
    
    
    
```

During a dispatch, the routes are verified individually for a matching HTTP method and a path pattern.
Route path patterns can include zero, one, or two path-extracting variables, possibly combined with regexes and the wildcard symbol "*"
The matched variable values become available as the correspondent handlers' parameters alongside the request details object.
The route's handler function should return the page's state:

```java
    get("/users/*", req -> state.ofUsers(List.of(user1, user2)))
```

Use ``match()`` DSL function routing to implement custom matching logic, for example:

```java
    match(req -> req.queryParam("name").isPresent(), req -> State.of(req.queryParam("name")));
```

The ``any()`` route matches every request.

### DOM elements references

The ``propertiesByRef()`` method of a DOM event context object provides access to the client-side elements properties.

```java
    final ElementRef inputRef = createElementRef();
    ...
    input(elementId(inputRef),
          attr("type", "text")),
    a("#", "Click me", on("click", ctx -> {
            var props = ctx.propertiesByRef(inputRef);
            props.getString("value").thenAccept(value -> { 
                                                  System.out.println("Input element's value: " + value);
                                                  props.setState("value", "new text");           
            });     
    }))
```

A reference to an object can be created on-the-fly using ``RefDefinition.withKey()`` method.

There is also the special ``window().ref()`` reference for the page's window object.

### Evaluating JS code on client-side

To invoke arbitrary EcmaScript code in the browser use the ``ctx.evalJs()`` method.
``evalJs()`` sends a code fragment to the client and returns its evaluation result as a  ``CompletableFuture<JsonDataType>``.

```java
    ...
        button(attr("type", "button"),
               text("Alert"),
               on("click",
                  ctx -> ctx.evalJs("alert('Hello from the server')"))),
        button(attr("type", "button"),
               text("Calculate"),
               on("click",
                  ctx -> ctx.evalJs("1+1").whenComplete((r,e) -> System.out.println("1+1=" + r)))
   ...
```

### Web server's configuration

The ``rsp.jetty.WebServer`` class constructor accepts extra parameters like an optional static resources' handler
and a TLS/SSL configuration:

```java
    final var staticResources = new StaticResources(new File("src/main/java/rsp/tetris"), "/res/*");
    final var sslConfig = SslConfiguration("/keysore/path", "changeit");
    final var server = new WebServer(8080, app, staticResources, sslConfig);
    server.start();
    server.join();
```

### Logging

This project's uses ``System.Logger`` for server-side logging.

On the client-side, to enable detailed diagnostic data exchange logging, enter in the browser console:

```javascript
  RSP.setProtocolDebugEnabled(true)
```