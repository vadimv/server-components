# RSP
[![javadoc](https://javadoc.io/badge2/io.github.vadimv/rsp/javadoc.svg)](https://javadoc.io/doc/io.github.vadimv/rsp)
[![maven version](https://img.shields.io/maven-central/v/io.github.vadimv/rsp)](https://search.maven.org/search?q=io.github.vadimv)

The Reactive Server Pages project enables a Java developer to create real-time single-page applications and UI components
with server-side HTML rendering.

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
where lambda's parameter ``s`` is a read-and-write accessor ``UseState<S>``.
Where ``S`` state type is an immutable class or record representing the application state.
Call the ``get()`` method of the accessor to read the current state.

Use the utility ``of()`` function for rendering a ``Stream<S>`` of objects, e.g. a list, or a table rows. 

```java
    import static rsp.dsl.Html.*;
    ...
    s -> ul(of(s.get().items.stream().map(item -> li(item.name))))
```

or an overloaded variant which accepts a ``CompletableFuture<S>``:
```java
    final Function<Long, CompletableFuture<String>> service = userDetailsService(); 
    ...
         // let's consider that at this moment we know the current user's Id
    s -> div(of(service.apply(s.get().user.id).map(str -> text(str))))
```

another overloaded variant gets a ``Supplier<S>`` as its argument.
This version if the ``of()`` function is for code fragments with imperative logic. 
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

The ``when()`` function conditionally renders an element.
```java
    s -> when(s.get().showLabel, span("This is a label"))
```

### Plain HTML pages

The ``head()`` function creates an HTML ``head`` tag for a Single Page Application type page.
This type of header contains a script, that enables WebSocket communication with the server after the page loads.

The ``plainHead()`` renders the markup with the ``head`` tag without this script resulting in a plain HTML page.
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

### Events

Register a browser's page DOM event handler using ``on(eventType, handler)``.
On an event the handler's runs Java code on the server side.

```java
    s -> a("#", "Click me", on("click", ctx -> {
                System.out.println("Clicked " + s.get().counter + " times");
                s.accept(new State(s.get().counter + 1));
            }));
    ...
    static class State { final int counter; State(int counter) { this.counter = counter; } }
```
An event handler's code usually sets a new application's state snapshot by invoking one of overloaded ``UseState<S>.accept()`` methods.
A new state triggers re-rendering the page on the server.

The event handler's ``EventContext`` parameter has a number of useful methods.  
One of these methods allows access to client-side document elements properties values via elements references.

```java
    final ElementRef inputRef = createElementRef();
    ...
    input(inputRef,
          attr("type", "text")),
    a("#", "Click me", on("click", ctx -> {
            ctx.props(inputRef).getString("value").thenAccept(value -> System.out.println("Input's value: " + value));     
    }))
```

A reference to an object can be created on-the-fly using ``RefDefinition.withKey()`` method.
  
The ``EventContext.eventObject()`` method reads the event's object as a JSON-like data structure:

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

### Routing and path mapping

To resolve an initial application state from an HTTP request during the first rendering, create a routing function and
provide it as an application's parameter:

```java
    private CompletableFuture<State> routes(HttpRequest request) {
        return request.method == HttpRequest.Methods.GET ? route(request.path) : State.page404();
    }
    
    public CompletableFuture<State> route(Path path) {
        final Path.Matcher<State> m = path.createMatcher(State.page404()) // a default match
                                          .match((name) -> true,                 // /{name}
                                                (name) -> db.getList(name).map(list -> State.of(list)))
                                          .match((name, id) -> isNumeric(id),    // /{name}/{id}
                                                (name, id) -> db.getOne(Long.parse(id)).map(instance -> State.of(instance)));
        
        return m.result;
    }

    ...    
    final App<State> app = new App<>(this::routes,
                                     new PageLifeCycle.Default<>(),
                                     render());
```

The default request-to-state routing implementation returns a provided initial state for any incoming HTTP request.

In a kind of opposite way, the current application's state can be mapped to the browser's navigation bar path using another function,
which corresponds to another parameter of the ``App`` constructor.
 
```java
     public static Path state2path(State state) {
        //  /{name}/{id} or /{name}
        return state.details.map(details -> new Path(state.name, Long.toString(details.id))).or(new Path(state.name));
    }
```
The default state-to-path routing sets an empty path for any state.

### Components

An application is composed of components. A component is a Java class implementing ``Component<S>`` interface.

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
providing a state object and optionally a listener, delivering the state change from a child component
to propagate this change to its parent state's consumer. 

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


### Page lifecycle events listener

Provide an instance of ``PageLifecycle`` interface as an optional parameter when creating an 
application object. This parameter allows listening to the events, specifically the event before to a page creation and 
after the page shutdown.

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

### Application and server's configuration

See the ``rsp.AppConfig`` class for an application configuration's details.

A web server's ``rsp.jetty.JettyServer`` class constructor accepts parameters providing the application's web context base path 
as well as an optional static resources' handler and a TLS/SSL connection's configuration.

### Logging

By default, internally, the project uses a console logger.
Set ``rsp.log.level`` system property to change its log level, for example ``-Drsp.log.level=trace``.

To use an Slf4j logger instead of the default console logger, provide the ``Slf4jLogReporting`` logger implementation to
the ``AppConfig`` application configuration. 

To enable client-side detailed diagnostic data exchange logging, enter in the browser's console:

```javascript
  RSP.setProtocolDebugEnabled(true)
```

### Schedules

The ``EventContext.schedule()`` and ``EventContext.scheduleAtFixedRate()`` 
methods allow submitting of a delayed or periodic action that can be cancelled. 
An optional schedule name parameter may be provided when creating a new schedule. 
Later this name could be used for the schedule cancellation.
Scheduled tasks will be executed in threads from the internal thread pool,
see the synchronized versions of ``accept()`` and ``acceptOptional()`` methods of the live page object accepting lambdas.

```java
    final static TimerRef TIMER_0 = createTimerRef();
    ...
    button(attr("type", "button"),
           text("Start"),
           on("click", c -> c.scheduleAtFixedRate(() -> System.out.println("Timer event")), TIMER_0, 0, 1, TimeUnit.SECONDS))),
    button(attr("type", "button"),
               text("Stop"),
               on("click", c -> c.cancelSchedule(TIMER_0)))
```

## How to build and run tests

To build the project from the sources:

```shell script

$ mvn clean package
```

To run all the tests:

```shell script

$ mvn clean test -Ptest-all
```

## Browser and server communications diagram

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

A common approach to build a web UI today is to break it to the server and client-side and connect them with some kind of remote API.
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





   