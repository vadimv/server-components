# RSP
[![javadoc](https://javadoc.io/badge2/io.github.vadimv/rsp/javadoc.svg)](https://javadoc.io/doc/io.github.vadimv/rsp)
[![maven version](https://img.shields.io/maven-central/v/io.github.vadimv/rsp)](https://search.maven.org/search?q=io.github.vadimv)

The Reactive Server Pages (RSP) is a lightweight Java server-state web framework. 
RSP enables creation of real-time single-page applications and plain HTML webpages.

### Hello World

Java version >= 11.

The Maven dependency:
```xml
    <dependency>
        <groupId>io.github.vadimv</groupId>
        <artifactId>rsp</artifactId>
        <version>0.5</version>
    </dependency>
```

The *Hello World* application's code:
```java
    import rsp.App;
    import rsp.jetty.JettyServer;
    
    import static rsp.dsl.Html.*;
    
    public class HelloWorld {
        public static void main(String[] args) {
            final var app = new App<>("Hello world!",
                                      s -> html(
                                                body(
                                                     p(s.get())
                                                    )
                                                )
                                      );
            final var server = new JettyServer(8080, "", app);
            server.start();
            server.join();
        }
    }
```
Run the class and navigate to http://localhost:8080.

### Code examples

* [TODOs list](https://github.com/vadimv/rsp-todo-list)
* [Hacker News API client](https://github.com/vadimv/rsp-hn)
* [Conway's Game of Life](https://github.com/vadimv/rsp-game-of-life)
* [Tetris](https://github.com/vadimv/rsp-tetris)

### HTML markup Java DSL

Use the RSP Java internal domain-specific language (DSL) for declarative definition of an HTML page markup.
For example, re-write the HTML fragment below:

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

in Java DSL as

```java
    import static rsp.dsl.Html.*;
    ...
    s -> html(
              body(
                   h1("This is a heading"),
                   div(attr("class", "par"), 
                       p("This is a paragraph"),
                       p(s.get().text)) // adds a paragraph with a text from the state object's 'text' field
                  ) 
            );
```
- HTML tags are represented by the ``rsp.dsl.Html`` class' methods with same names, e.g. ``<div></div>``  translates to ``div()``
- HTML attributes are represented by the ``rsp.dsl.Html.attr(name, value)`` function, e.g. ``class="par"`` translates to ``attr("class", "par")``
- The lambda parameter's ``s.get()`` method reads the current state snapshot

The utility ``of()`` DSL function renders a ``Stream<T>`` of objects, e.g. a list, or a table rows:
```java
    import static rsp.dsl.Html.*;
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
    import static rsp.dsl.Html.*;
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

### Plain HTML pages

There are two types of web pages:
- Single-page application (SPA) connected pages
- Plain detached pages

An RSP web application can contain a mix of both types. 
For example, an admin part can be a single-page application page, and the client facing part made of plain pages.

The ``head()`` function creates an HTML ``head`` tag for an SPA type page.
This type of header injects a script, which establishes a WebSocket connection between the browser's page and the server 
and enables reacting to the browser events.

The ``plainHead()`` renders the markup with the ``head`` tag without injecting of this script resulting in a plain detached HTML page.

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

### Single-page application's events

Register a browser's page DOM event handler by adding an ``on(eventType, handler)`` to an HTML tag:

```java
    s -> a("#", "Click me", on("click", ctx -> {
                System.out.println("Clicked " + s.get().counter + " times");
                s.accept(new State(s.get().counter + 1));
            }));
    ...
    static class State { final int counter; State(int counter) { this.counter = counter; } }
```
When an event occurs:
- the page sends the event data to the server
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

### HTTP Request routing and path mapping

To resolve an initial application's state from an HTTP request during the page's initial rendering,
create a routing function and provide it as an application's constructor parameter:

```java
    private CompletableFuture<State> routes(HttpRequest request) {
        return request.method == HttpRequest.Methods.GET ? route(request.path) : State.page404();
    }
    
    private CompletableFuture<State> route(Path path) {
        final Path.Matcher<State> m = new Path.Matcher(path, State.page404())    // a default match
                                          .match((name) -> true,                 // /{name}
                                                 (name) -> db.getList(name).map(list -> State.of(list)))
                                          .match((name, id) -> isNumeric(id),    // /{name}/{id}, where id is a number
                                                 (name, id) -> db.getInstance(Long.parse(id)).map(instance -> State.of(instance)));
        
        return m.state;
    }

    ...    
    final App<State> app = new App<>(this::routes,
                                     new PageLifeCycle.Default<>(),
                                     render());
```

The root component or a descendant component maps a routed result state to a specific view. 
See [HTML markup Java DSL](#html-markup-java-dsl).

For SPAs, the current application's state can be mapped to the browser's navigation bar path using another function
provided as another parameter of the ``App`` class constructor.
 
```java
     public static Path state2path(State state) {
        //  /{name}/{id} or /{name}
        return state.details.map(details -> new Path(state.name, Long.toString(details.id))).or(new Path(state.name));
    }
```
If not provided explicitly, the default state-to-path routing sets an empty path for any state.

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

A component's ``render()`` method invokes ``render()`` methods of its descendant components,
providing a descendant's component's state object, which normally is a part of the application's state tree and a listener.
The listener's code propagates the state change from a child component to its parent state's consumer,
up to the root component's context. 

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


### Page lifecycle events

Provide an implementation of the ``PageLifecycle`` interface as a parameter on an application's constructor.
This allows to listen to the page's lifecycle events:
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

```
Add these listeners, for example, when you need to subscribe to some messages stream on a page live session creation
and unsubscribing when the page closes.

### Application and server's configuration

See the ``rsp.AppConfig`` class for an application configuration's details.

A web server's ``rsp.jetty.JettyServer`` class constructor accepts parameters providing the application's web context base path 
as well as an optional static resources' handler and a TLS/SSL connection's configuration.

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

### Browser and server interaction diagram

```
     ┌───────┐                   ┌──────┐
     │Browser│                   │Server│
     └───┬───┘                   └──┬───┘
         │         HTTP GET         │    
         │──────────────────────────>    
         │                          │  Inital page render 
         │    HTTP response 200     │    
         │<─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─     
         │                          │    
         │    Open a WebSocket      │    
         │──────────────────────────>    
         │                          │  Create a new live page  
         │   Register page events   │    
         │<─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─     
         │                          │    
         │   An event on the page   │    
         │ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─>    
         │                          │   Re-render, calculate virtual DOM diff 
         │   Modify DOM commands    │    
         │<─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─     
     ┌───┴───┐                   ┌──┴───┐
     │Browser│                   │Server│
     └───────┘                   └──────┘
```

## Motivation

The common approach to build a web UI today is to break it to the server and client-side and connect them with some kind of remote API.
Often, these two sides even use different programming languages, dependency management, and build tools.
This all introduces a lot of accidental complexity.
Any change made on the client-side potentially needs to be reflected on the API and the server-side.
Isn't it sounds like developing and supporting effectively two applications instead of one?

An RSP UI lives on the server-side and abstracts the client-side.
The browser acts more like an equivalent of a terminal for Unix X Window System, a thin client.
After loading an initial page HTML, it only feeds events to the server and updates the presentation to the incoming diff commands.

An RSP application developer's experience may feel similar to creating a desktop application UI in reactive style with HTML and CSS.
Or like creating a React application with direct access to its backend data.

Some other bonuses of this approach are:
- coding and debugging the UI is just coding in plain Java and debugging Java;
- fast initial page load no matter of the application's size;
- your code always stays on your server;
- SEO-friendly out of the box.

Known concerns to deal with:
- may be not a good fit for use cases requiring very low response time, heavy animations, etc;
- latency between a client and the server should be low enough to ensure a good user experience;
- more memory and CPU resources may be required on the server;
- as for a stateful app, for scalability some kind of sticky session management required;
- a question of how to integrate RSP with existing JavaScript and CSS codebase needs to be addressed. 





   