# rsp
[![javadoc](https://javadoc.io/badge2/io.github.vadimv/rsp/javadoc.svg)](https://javadoc.io/doc/io.github.vadimv/rsp)
[![maven version](https://img.shields.io/maven-central/v/io.github.vadimv/rsp)](https://search.maven.org/search?q=io.github.vadimv)

* [About](#about)
* [Maven](#maven)
* [Code examples](#code-examples)
* [Routing](#routing)  
* [HTML markup Java DSL](#html-markup-java-dsl)
* [SPAs and plain pages head tag](#spas-and-plain-pages-head-tag)
* [Page HTTP status code and HTTP headers](#page-http-status-code-and-http-headers)
* [UI Stateful components](#ui-stateful-components)
* [Components state model](#components-state-model)
* [DOM events](#dom-events)
* [Navigation bar URL path and components state mapping](#navigation-bar-url-path-and-components-state-mapping)
* [DOM elements references](#dom-elements-references)
* [Evaluating JavaScript code on the client-side](#evaluating-javascript-code-on-the-client-side)
* [Navigation bar URL path](#navigation-bar-url-path-and-components-state-mapping)
* [Page lifecycle events](#page-lifecycle-events)
* [Application and server's configuration](#application-servers-configuration)
* [Schedules and timers](#schedules)
* [Logging](#logging)
* [How to build the project and run tests](#how-to-build-the-project-and-run-tests)

## About

rsp is a lightweight modern server-side web framework for Java.

With rsp, for a typical web application, two types of web pages are supported:
- single-page application (SPA), server-rendered, with page's live session and its state on the server, e.g. for the admin UI
- plain server-rendered detached HTML pages

### Maven

This project requires Java version 17 or newer.

To start using it, add the dependency:
```xml
    <dependency>
        <groupId>io.github.vadimv</groupId>
        <artifactId>rsp</artifactId>
        <version>1.0</version>
    </dependency>
```

### Code examples

* [Hello World](src/main/java/rsp/examples/HelloWorld.java)
* [TODOs list](https://github.com/vadimv/rsp-todo-list)
* [Tetris](https://github.com/vadimv/rsp-tetris)
* [Conway's Game of Life](https://github.com/vadimv/rsp-game-of-life)
* [Hacker News API client](https://github.com/vadimv/rsp-hn)

### Routing

An initial page generation consists of two phases:
- routing an incoming HTTP request and/or URL paths resulting in setup of the page's components immutable state objects
- rendering these state objects into the components views resulting a HTTP response

To define a routing of an incoming request, create a ``Routing`` object for components and/or provide it as an application's constructor parameter:

```java
    import static rsp.html.RoutingDsl.*;
    ...
    final App<State> app = new App<>(routes(), render());
    ...
    private static Routing<HttpRequest, State> routes() {
        final var db = new Database();
        return routing(get("/articles", req -> db.getArticles().thenApply(articles -> State.ofArticles(articles))),
                      get("/articles/:id", (__, id) -> db.getArticle(id).thenApply(article -> State.ofArticle(article))),
                      get("/users/:id", (__, id) -> db.getUser(id).thenApply(user -> State.ofUser(user))),
                      post("/users/:id(^\\d+$)", (req, id) -> db.setUser(id, req.queryParam("name")).thenApply(result -> State.userWriteSuccess(result))),
                      NotFound.INSTANCE);
    }
```
During a dispatch, routes are verified one by one for a matching HTTP method and path pattern. 
Routes path patterns can include zero, one or two path-variables, possibly combined with regexes and the wildcard symbol "*".
The matched variables values become available as the correspondent handlers' parameters alongside with the request details object.
The route's handler function should return a ``CompletableFuture`` of the page's state:

```java
    get("/users/*", req -> CompletableFuture.completedFuture(State.ofUsers(List.of(user1, user2))))
```

If needed, extract a paths-specific routing section:

```java
    final Routing<HttpRequest, State> routing = routing(concat(get(__ -> paths()),
                                                        State.pageNotFound()));
    
    private static PathRoutes<State> paths() {
         return concat(path("/articles", db.getArticles().thenApply(articles -> State.ofArticles(articles))),
                       path("/articles/:id", id -> db.getArticle(id).thenApply(article -> State.ofArticle(article)));
    }
```

Use ``match()`` DSL function routing to implement custom matching logic, for example:

```java
    match(req -> req.queryParam("name").isPresent(), req -> CompletableFuture.completedFuture(State.of(req.queryParam("name"))))
```

The ``any()`` route matches every request.

### HTML markup Java DSL

rsp provides a Java internal domain-specific language (DSL) for declarative definition of HTML views as a composition of functions.

For example, to re-write the HTML fragment:

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

provide the DSL Java code as below:

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
- HTML tags are represented by the ``rsp.html.HtmlDsl`` class' methods with same names, e.g. ``<div></div>``  translates to ``div()``
- HTML attributes are represented by the ``rsp.html.HtmlDsl.attr(name, value)`` function, e.g. ``class="par"`` translates to ``attr("class", "par")``
- the lambda's parameter `state` is the current state snapshot

The utility ``of()`` DSL function renders a ``Stream<T>`` of objects, e.g. a list, or a table rows:
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
    state -> newState -> div(of(lookupService.apply(state.user.id).map(str -> text(str))))
```

Another overloaded ``of()`` function takes a ``Supplier<S>`` as its argument and allows inserting code fragments
with imperative logic.
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

#### SPAs and plain pages head tag

The page's ``<head>`` tag DSL determines if this page is an SPA or plain.

The ``head(...)`` or ``head(PageType.SPA, ...)`` function creates an HTML page ``<head>`` tag for an SPA.
If the ``head()`` is not present in the page's markup, the simple SPA-type header is added automatically.
This type of head injects a script, which establishes a WebSocket connection between the browser's page and the server
and enables reacting to the browser events.

Using ``head(HeadType.PLAIN, ...)`` renders the markup with the ``<head>`` tag without injecting of init script
to establish a connection with server and enable server side events handling for SPA.
This results in rendering of a plain detached HTML page.

#### Page HTTP status code and HTTP headers

The ``statusCode()`` and ``addHeaders()`` methods enable to change result response HTTP status code and headers.
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
### UI Stateful Components

Actually, SPA pages are composed of components of two kinds:
- Stateful components, and
- Stateless views

A stateful component has its own mutable state associated.
Use Component's DSL  ``component()`` overloaded functions to create a stateful component.

Stateless views used for representation only and do not have a mutable state and effectively is a pure function
from a state to a DOM fragment's definition.

```java
    import static rsp.component.ComponentDsl.*;

    public static ComponentView<String> buttonView = state -> newState -> input(attr("type", "button"),
                                                                                attr("value", state),      
                                                                                on("click", ctx -> newState.set("Clicked")));

    ...
    div(
        span("Click the button below"),
        component("Ready",
                  buttonView)
    )   
    ...    

```

An application's top-level ``ComponentDefintion<S>`` is the root of its component tree.

### Components state model

A component's state is modeled as a finite state machine (FSM) and managed by the framework.
Any state change must be initiated by invoking of one of the ``NewState`` interface methods, like ``set()`` and ``apply()``.
Normally, state transitions are triggered by the browser's events.

The following example shows how a page state can be modelled using records, sealed interfaces and pattern matching:

```java
    sealed interface State permits UserState, UsersState {}
    record UserState(User user) implements State {}
    record UsersState(List<User> users) implements State {}
    
    record User(long id, String name) {}
        

    /**
     * The page's renderer, called by the framework as a result of a state transition.
     */
    static View<State> pageView() {
        return state -> switch (state) {
            case UserState  user -> userView().render(user);
            case UsersState users -> usersView().render(users);
        };
    }

    private static View<UserState> userView() { return state -> span("User:" + state); }
    private static View<UsersState> usersView() { return state -> span("Users list:" + state); }
```

A component's initial state can be provided is the following ways:
- set explicitly
- mapped from an HTTP request
- mapped from a request's URL path

### DOM events

To respond to browser events, register a page DOM event handler by adding an ``on(eventType, handler)`` to an HTML tag in the DSL:

```java
    state -> newState -> a("#", "Click me", on("click", ctx -> {
                                System.out.println("Clicked " + state.counter + " times");
                                newState.set(new State(s.get().counter + 1));
            }));
    ...
    static final class State { final int counter; State(final int counter) { this.counter = counter; } }
```

When an event occurs:
- the page sends the event data message to the server
- the system fires its registered event handler's Java code

An event handler's code usually sets a new application's state snapshot, calling one of the overloaded ``UseState<S>.accept()`` methods on the application state accessor.

A new set state snapshot triggers the following sequence of actions:
- the page's virtual DOM re-rendered on the server
- the difference between the current and the previous DOM trees is calculated  
- the diff commands sent to the browser
- the page's JavaScript program updates the presentation

The event handler's ``EventContext`` class parameter has a number of utility methods.  

Some types of browser events, like a mouse move, may fire a lot of events' invocations. 
Sending all these notifications over the network and processing them on the server side may cause the system's overload.
To filter the events before sending use the following event object's methods:
- ``throttle(int timeFrameMs)``
- ``debounce(int waitMs, boolean immediate)``

```java
    html(window().on("scroll", ctx -> {
            System.out.println("A throtteld page scroll event");
            }).throttle(500),
        ...
        )
```

The context's ``EventContext.eventObject()`` method reads the event's object as a JSON data structure:

```java
    form(on("submit", ctx -> {
            // Prints the submitted form's input field value
            System.out.println(ctx.eventObject());
         }),
        input(attr("type", "text"), attr("name", "val")),
        input(attr("type", "button"), attr("value", "Submit"))
    )
```
Events code runs in a synchronized sections on a live page session state container object.

### Navigation bar URL path and components state mapping

A component's state can be mapped to the browser's navigation bar path. 
With that, the current navigation path will be automatically converted to a component's state and vis-a-versa,
the state transition will cause the navigation path to be updated accordingly.

The "Back" and "Forward" browser's history buttons clicks initiate state transitions.

This is an example, where the first element of a path is mapped to an integer id, and id is mapped to the first element of the path:

```java
    import static rsp.component.ComponentDsl.*;

    static SegmentDefinition component() {
        return component( new Routing<>(path("/:id(^\\d+$)/*", id -> CompletableFuture.completedFuture(Integer.parseInt(id))), -1),
                          (id, path) -> Path.of("/" + id + "/" + path.get(0)),
                          componentView());
    }

```
If not configured, the default state-to-path mapping sets an empty path for any state.

### DOM elements references

One of the event context object's methods allows access to client-side document elements properties values by elements references.

```java
    final ElementRef inputRef = createElementRef();
    ...
    input(inputRef,
          attr("type", "text")),
    a("#", "Click me", on("click", ctx -> {
            ctx.props(inputRef).getString("value").thenAccept(value -> System.out.println("Input's value: " + value));     
    }))
```

A reference to an object also can be created on-the-fly using ``RefDefinition.withKey()`` method.

There is the special ``window()`` reference for the page's window object.

The ``window().on(eventType, handler)`` method registers a window event handler:

```java
    html(window().on("click", ctx -> {
            System.out.println("window clicked");
        }),
        ...
        )
```

### Evaluating JavaScript code on the client-side

To invoke arbitrary JavaScript in the browser use the ``ctx.evalJs()`` method of an event's context object.
``ctx.evalJs()`` returns the evaluation result as an object of  ``CompletableFuture<JsonDataType>``.

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

### Page lifecycle events

Provide an implementation of the ``PageLifecycle`` interface as a parameter on an application's constructor.
This allows to listen to an SPA page's lifecycle events:
- before the page is created
- after the page is closed

```java
    final PageLifeCycle<Integer> plc = new PageLifeCycle.Default<Integer>() {
        @Override
        public void pageCreated(QualifiedSessionId sid, Integer initialState, NewState<Integer> newState) {
            final Thread t = new Thread(() -> {
                try {
                    Thread.sleep(10_000);
                    newState.apply(s -> s + 1);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            });
            t.start();
        }
    };
    
    final App<Optional<FullName>> app = new App<>(route(), pages()).pageLifeCycle(plc);
    ...
```
Use these listeners, when you need to subscribe to some messages stream on a page live session creation
and unsubscribing when the page closes.

### Application server's configuration

Use an instance of the ``rsp.AppConfig`` class as the parameter to the ``config`` method of an ``App`` object:

```java
    final var app = new App(routing(), rootCreateViewFunction()).config(AppConfig.DEFAULT);
```
A web server's ``rsp.jetty.JettyServer`` class constructor accepts parameters like the application's web context base path,
an optional static resources' handler and a TLS/SSL connection's configuration:

```java
    final var staticResources = new StaticResources(new File("src/main/java/rsp/tetris"), "/res/*");
    final var sslConfig = SslConfiguration("/keysore/path", "changeit");
    final var server = new JettyServer(8080, "/base", app, staticResources, sslConfig);
    server.start();
    server.join();
```

### Logging

This project's uses ``System.Logger`` for server-side logging.

On the client-side, to enable detailed diagnostic data exchange logging, enter in the browser console:

```javascript
  RSP.setProtocolDebugEnabled(true)
```

### Schedules

The framework provides the ``EventContext.schedule()`` and ``EventContext.scheduleAtFixedRate()`` utility methods 
which allows to submit a delayed or periodic action that can be cancelled.
A timer's reference parameter may be provided when creating a new schedule. 
Later this reference could be used for the schedule cancellation.

```java
    final static TimerRef TIMER_0 = TimerRef.createTimerRef();
    ...
    button(attr("type", "button"),
           text("Start"),
           on("click", c -> c.scheduleAtFixedRate(() -> System.out.println("Timer event")), TIMER_0, 0, 1, TimeUnit.SECONDS))),
    button(attr("type", "button"),
               text("Stop"),
               on("click", c -> c.cancelSchedule(TIMER_0)))
```

### How to build the project and run tests

To build the project from the sources:

```shell script

$ mvn clean package
```

To run all the tests:

```shell script

$ mvn clean test -Ptest-all
```