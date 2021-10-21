# RSP
[![javadoc](https://javadoc.io/badge2/io.github.vadimv/rsp/javadoc.svg)](https://javadoc.io/doc/io.github.vadimv/rsp)
[![maven version](https://img.shields.io/maven-central/v/io.github.vadimv/rsp)](https://search.maven.org/search?q=io.github.vadimv)

The Reactive Server Pages (RSP) is a modern lightweight Java server-state web framework. 
RSP enables creation of real-time single-page applications and plain HTML webpages.

* [Project's motivation](#motivation)
* [Maven](#maven)
* [Code examples](#code-examples)
* [HTTP requests routing](#http-requests-routing)  
* [HTML markup Java DSL](#html-markup-rendering-java-dsl)
* [Page state model](#page-state-model)
* [Single-page application](#single-page-application)
* [Navigation bar URL path](#navigation-bar-url-path)
* [UI Components](#ui-components)
* [Plain HTML pages](#plain-html-pages)
* [Page lifecycle events](#page-lifecycle-events)
* [Application and server's configuration](#application-and-servers-configuration)
* [Schedules](#schedules)
* [How to build the project and run tests](#how-to-build-the-project-and-run-tests)

## Motivation

A popular approach for a Java backend based web UI is to build the client-side with tools
like React or Vue and fetch the data from the server with some kind of remote API.

JavaScript client programming is extremely powerful, but this scheme introduces a lot of complexity.
Any change made on the client-side potentially needs to be reflected on the API and the server-side.
The project's build requires on-boarding non-Java dependency management and build tools.

RSP aims for developing the web UI in Java while keeping external dependencies to the minimum.

In its case, after loading an initial page HTML, the browser feeds events to the server and updates the presentation to the incoming diff commands.
The page's state is maintained on the server.
The browser acts more like an equivalent of a terminal for Unix X Window System, a thin client.

As a result:
- coding and debugging the UI is just coding in plain Java and debugging Java;
- fast initial page load no matter of the application's size;
- your code always stays on your server;
- SEO-friendly out of the box.

### Maven

Use Java version 11 or newer.

Add the dependency:
```xml
    <dependency>
        <groupId>io.github.vadimv</groupId>
        <artifactId>rsp</artifactId>
        <version>0.6</version>
    </dependency>
```

### Code examples

* [Hello World](src/main/java/rsp/examples/HelloWorld.java)
* [TODOs list](https://github.com/vadimv/rsp-todo-list)
* [Hacker News API client](https://github.com/vadimv/rsp-hn)
* [Conway's Game of Life](https://github.com/vadimv/rsp-game-of-life)
* [Tetris](https://github.com/vadimv/rsp-tetris)

### HTTP requests routing

An RSP application's initial page rendering request-response workflow consist of two explicitly defined phases:
- routing an incoming request with a result of the page's immutable state object;
- rendering this state object into the result HTTP response.

To dispatch the incoming request, create a Routing object and provide it as an application's constructor parameter:

```java
    import static rsp.html.RoutingDsl.*;
    ...
    final App<State> app = new App<>(route(), render());
    ...
    private static Route<State> route()
        final var db = new Database();
        return concat(get("/articles", req -> db.getArticles().thenApply(articles -> State.ofArticles(articles))),
                      get("/articles/:id", (__, id) -> db.getArticle(id).thenApply(article -> State.ofArticle(article))),
                      get("/users/:id", (__, id) -> db.getUser(id).thenApply(user -> State.ofUser(user))),
                      post("/users/:id(^\\d+$)", (req, id) -> db.setUser(id, req.queryParam("name")).thenApply(result -> State.userWriteSuccess(result))));
    }
```
During a dispatch, routes verified one by one for a matching HTTP method and path pattern. 
Route path patterns can include zero, one or two path-variables, possibly combined with regexes and the wildcard symbol "*".
The matched variables values become available as the correspondent handler functions ``String`` parameters alongside with the request details object.
The route's handler function should return a ``CompletableFuture`` of the page's state:

```java
    get("/users/*", req -> CompletableFuture.completedFuture(State.ofUsers(List.of(user1, user2))))
```

If needed, extract a paths-specific routing section:

```java
    final Route<HttpRequest, State> routes = concat(get(__ -> paths()),
                                                    any(State.pageNotFound()));
    
    private static PathRoutes<State> paths() {
         return concat(path("/articles", db.getArticles().thenApply(articles -> State.ofArticles(articles))),
                       path("/articles/:id", id -> db.getArticle(id).thenApply(article -> State.ofArticle(article)));
    }
```

Use ``match()`` DSL function routes to implement custom matching logic, for example:

```java
    match(req -> req.queryParam("name").isPresent(), req -> CompletableFuture.completedFuture(State.of(req.queryParam("name"))))
```

The ``any()`` route matches every request.

### HTML markup rendering Java DSL

On the rendering phase a state object to be converted to HTML.

RSP provides the Java internal domain-specific language (DSL) for declarative definition of an HTML page markup
as functions composition.

For example, to re-write the HTML fragment below:

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

provide the Java code:

```java
    import static rsp.html.HtmlDsl.*;
    ...
    public Component<State> render() {
        return s -> html(
                      body(
                           h1("This is a heading"),
                           div(attr("class", "par"), 
                               p("This is a paragraph"),
                               p(s.get().text)) // adds a paragraph with a text from the state object's 'text' field
                          ) 
                    );
    }
```
where:
- HTML tags are represented by the ``rsp.html.HtmlDsl`` class' methods with same names, e.g. ``<div></div>``  translates to ``div()``
- HTML attributes are represented by the ``rsp.html.HtmlDsl.attr(name, value)`` function, e.g. ``class="par"`` translates to ``attr("class", "par")``
- The lambda parameter's ``s.get()`` method reads the current state snapshot

The utility ``of()`` DSL function renders a ``Stream<T>`` of objects, e.g. a list, or a table rows:
```java
    import static rsp.html.HtmlDsl.*;
    ...
    s -> ul(of(s.get().items.stream().map(item -> li(item.name))))
```

An overloaded variant of ``of()`` accepts a ``CompletableFuture<S>``:
```java
    final Function<Long, CompletableFuture<String>> service = userDetailsService(); 
    ...
         // let's consider that at this moment we know the current user's Id
    s -> div(of(service.apply(s.get().user.id).map(str -> text(str))))
```

Another overloaded ``of()`` function takes a ``Supplier<S>`` as its argument and allows inserting code fragments
with imperative logic.
```java
    import static rsp.html.HtmlDsl.*;
    ...
    s -> of(() -> {
                     if (s.get().showInfo) {
                         return p(s.get().info);
                     } else {
                         return p("none");
                     }       
                 })
```

The ``when()`` DSL function conditionally renders (or not) an element:
```java
    s -> when(s.get().showLabel, span("This is a label"))
```

### Page state model

Model an RSP application page's state as a finite state machine (FSM).
An HTTP request routing resolves an initial state. 
Page events, like user actions or timer events trigger state transitions.

The following example shows how a page state can be modelled using records, 
sealed interfaces and pattern matching in Java 17:

```java
    sealed interface State permits UserState, UsersState {}
    record UserState(User user) implements State {}
    record UsersState(List<User> users) implements State {}
    
    record User(long id, String name) {}

    /**
     * An event handler, makes the page's FSM transition.
     */
    public static void userSelected(UseState<State> s, User user) {
        s.accept(new UserState(user));
    }

    /**
     * The page's renderer, called by the framework as a result of a state transition.
     */
    static Component<State> render() {
        return s -> switch (s.get()) {
            case UserState  state  -> userView().render(state);
            case UsersState state  -> usersView().render(state);
        };
    }

    private static Component<UserState> userView() { return s -> span("User:" + s.get()); }
    private static Component<UsersState> usersView() { return s -> span("Users list:" + s.get()); }
```

### Single-page application

RSP supports two types of web pages:
- Single-page application (SPA) with establishing the page's live session and keeping its state on the server
- Plain detached pages

An RSP web application can contain a mix of both types.
For example, an admin part can be a single-page application page, and the client facing part made of plain pages.

The type of page to be rendered is determined by the page's head tag DSL function.

The ``head()`` function creates an HTML page ``head`` tag for an SPA.
If the ``head()`` is not present in the page's markup, the simple SPA-type header is added automatically.
This type of header injects a script, which establishes a WebSocket connection between the browser's page and the server
and enables reacting to the browser events.

To respond to browser events register a page DOM event handler by adding an ``on(eventType, handler)`` to an HTML tag:

```java
    s -> a("#", "Click me", on("click", ctx -> {
                System.out.println("Clicked " + s.get().counter + " times");
                s.accept(new State(s.get().counter + 1));
            }));
    ...
    static class State { final int counter; State(int counter) { this.counter = counter; } }
```

When an event occurs:
- the page sends the event data message to the server
- the system fires its registered event handler's Java code.

An event handler's code usually sets a new application's state snapshot, calling one of the overloaded ``UseState<S>.accept()`` methods on the application state accessor.

A new set state snapshot triggers the following sequence of actions:
- the page's virtual DOM re-rendered on the server
- the difference between the current and the previous DOM trees is calculated  
- the diff commands sent to the browser
- the page's JavaScript program updates the presentation

The event handler's ``EventContext`` class parameter has a number of utility methods.  

One of these methods allows access to client-side document elements properties values by elements references.

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

Some types of browser events, like a mouse move, may fire a lot of invocations. 
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

The context's ``EventContext.eventObject()`` method reads the event's object as a JSON-like data structure:

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

### Plain HTML pages

Using ``plainHead()`` function instead of ``head()`` renders the markup with the ``head`` tag without injecting of this script
resulting in a plain detached HTML page.

The ``statusCode()`` and ``addHeaders()`` methods enable to change result response HTTP status code and headers.
For example:

```java
    s -> html(   
              plainHead(title("404 page not found")),
              body(
                   div(
                       p("404 page not found")
                  ) 
                )
            ).statusCode(404);
```

### UI Components

Pages are composed of components. A component is a Java class which implements the ``Component<S>`` interface.

```java
    public static Component<ButtonState> buttonComponent(String text) {
        return s -> input(attr("type", "button"),
                           attr("class", "button"),
                           attr("value", text),      
                           on("click", ctx -> s.accept(new ButtonState())));
        
    }
    public static class ButtonState {}
```

The ``render()`` method of a component usually invokes ``render()`` methods of its descendant components, providing as parameters: 
- a descendant's component's state object, normally a part of the application's state object tree
- a listener's code, a ``Consumer<S>`` implementation, which propagates the new state change from a child component to its parent,
up to the root component's context

```java
    ...
    public static Component<ConfirmPanelState> confirmPanelComponent(String text) {
        return s -> div(attr("class", "panel"),
                         span(text),
                         buttonComponent("Ok").render(new ButtonState(), 
                                                      buttonState -> s.accept(new ConfimPanelState(true))),
                         buttonComponent("Cancel").render(new ButtonState(), 
                                                          buttonState -> s.accept(new ConfimPanelState(false)));
        
    }
    public static class ConfirmPanelState {
        public final boolean confirmed;
        public ConfirmPanelState(boolean confirmed) { this.confirmed = confirmed; }
    }
```

An application's top-level ``Component<S>`` is the root of its component tree.

### Navigation bar URL path

During a Single Page Application session, the current app state mapping to the browser's navigation bar path can be configured using
the ``stateToPath`` method of an ``App`` object:

```java
    final App<State> app = new App<>(route(),
                                     pages())  //  If the details are present, set the path to /{name}/{id} or set it to /{name}
            .stateToPath((state, prevPath) -> state.details.map(details -> Path.absolute(state.name, Long.toString(details.id)))
                                                           .or(Path.absolute(state.name)));

```
If not configured, the default state-to-path mapping sets an empty path for any state.

### Page lifecycle events

Provide an implementation of the ``PageLifecycle`` interface as a parameter on an application's constructor.
This allows to listen to the SPA page's lifecycle events:
- before the page is created
- after the page is closed

```java
    final PageLifeCycle<Integer> plc = new PageLifeCycle.Default<Integer>() {
        @Override
        public void beforeLivePageCreated(QualifiedSessionId sid, UseState<Integer> useState) {
            final Thread t = new Thread(() -> {
                try {
                    Thread.sleep(10_000);
                    synchronized (useState) {
                        useState.accept(useState.get() + 1);
                    }
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
Add these listeners, for example, when you need to subscribe to some messages stream on a page live session creation
and unsubscribing when the page closes.

### Application and server's configuration

Provide an instance of the ``rsp.AppConfig`` class as the parameter to the ``config`` method of an ``App`` object.

A web server's ``rsp.jetty.JettyServer`` class constructor accepts parameters like the application's web context base path,
an optional static resources' handler and a TLS/SSL connection's configuration.

### Logging

By default, internally, the project uses a console logger.
Set the ``rsp.log.level`` system property to change the application's log level, for example ``-Drsp.log.level=trace``.

To use an Slf4j logger instead of the default console logger, provide the ``Slf4jLogReporting`` logger implementation to
the ``AppConfig`` application configuration. 

To enable client-side detailed diagnostic data exchange logging, enter in the browser's console:

```javascript
  RSP.setProtocolDebugEnabled(true)
```

### Schedules

The ``EventContext.schedule()`` and ``EventContext.scheduleAtFixedRate()`` 
methods allow submitting of a delayed or periodic action that can be cancelled. 
A timer reference parameter may be provided when creating a new schedule. 
Later this reference could be used for the schedule cancellation.
Scheduled tasks will be executed in threads from the internal thread pool,
see the synchronized versions of ``accept()`` and ``acceptOptional()`` methods of the live page object accepting
lambdas as parameters.

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