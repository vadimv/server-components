# RSP
[![javadoc](https://javadoc.io/badge2/io.github.vadimv/rsp/javadoc.svg)](https://javadoc.io/doc/io.github.vadimv/rsp)
![Maven Central](https://img.shields.io/maven-central/v/io.github.vadimv/rsp?color=yellogreen)
The Reactive Server Pages project enables a Java developer to create real-time single-page applications and UI components
with server-side HTML rendering.

## How it works

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

## Usage

This project requires Java 11 or newer. 

Maven dependency:
```xml
    <dependency>
        <groupId>io.github.vadimv</groupId>
        <artifactId>rsp</artifactId>
        <version>0.4</version>
    </dependency>
```

To build the project from the sources:

```shell script

$ mvn clean package

```
### Hello World and code examples

```java
    import rsp.App;
    import rsp.jetty.JettyServer;
    
    import static rsp.dsl.Html.*;
    
    public class HelloWorld {
        public static void main(String[] args) throws Exception {
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
Run the class and connect to http://localhost:8080.

See the [TODOs list](https://github.com/vadimv/rsp-todo-list),
[Hacker News API client](https://github.com/vadimv/rsp-hn),
[Conway's Game of Life](https://github.com/vadimv/rsp-game-of-life)
and [Tetris](https://github.com/vadimv/rsp-tetris) examples.

### HTML markup Java DSL

Use the RSP Java internal domain-specific language for declarative definition of an HTML page markup.
For example, the fragment:

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

should be written in Java code as

```java
    import static rsp.dsl.Html.*;
    ...
    s -> html(
              body(
                   h1("This is a heading"),
                   div(attr("class", "par"), 
                       p("This is a paragraph"),
                       p(s.get().text)) // this is another paragraph with a text from the current state object's field
                  ) 
            );
```

Use the utility ``of()`` function for rendering a ``Stream<S>`` of objects

```java
    import static rsp.dsl.Html.*;
    ...
    s ->
        ul(of(s.get().items.stream().map(item -> li(item.name))))
```

or an overloaded variant which accepts a ``CompletableFuture<S>``:
```java
    final Function<Long, CompletableFuture<String>> service = userDetailsService(); 
    ...
    s ->
        // let's consider that at this moment we know the current user's Id
        div(of(service.apply(s.get().user.id).map(str -> text(str))))
```

another one is for code fragments with imperative logic, if operator branching, gets a ``Supplier<S>`` as its argument:
```java
    import static rsp.dsl.Html.*;
    ...
    s ->
        of(() -> {
            if (s.get().showInfo) {
                return p(s.get().info);
            } else {
                return p("none");
            }       
        })
```

Here, the ``span`` element will be visible or not depending on a boolean field of the state object using ``when()`` function:
```java
    s ->
        when(s.get().showLabel, span("This is a label"))
```

### Events

Register a handler for a browser event using the ``rsp.dsl.Html.on(eventType, handler)`` method.

```java
    s ->
        a("#", "Click me", on("click", ctx -> {
                    System.out.println("Clicked " + s.get().counter + " times");
                    s.accept(new State(s.get().counter + 1));
                }));
    ...
    static class State { final int counter; State(int counter) { this.counter = counter; } }
```
An event handler's code usually sets a new state snapshot object by invoking one of overloaded ``UseState<S>.accept()`` methods.

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

In the case when we need a reference to an object created on-the-fly use ``RefDefinition.withKey()`` method.
  
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
RSP executes events code in a synchronized on a live page session instance context.

### Components

An RSP application is composed of components. A component is a Java class implementing ``Component<S>`` interface.

```java
    public static Component<ButtonState> buttonComponent(String text) {
        return s -> input(attr("type", "button"),
                           attr("class", "button"),     
                           on("click", ctx -> s.accept(new ButtonState())),
                           text(text));
        
    }
    public static class ButtonState {}
```

A component's ``render()`` method invokes ``render()`` methods of its descendant components
with an instance of a specific child component's``UseState<S>`` wrapper class as an argument. 
Use one of the static utility functions in the ``UseState`` interface like ``readWrite()``,
``readOnly()`` or ``readWriteInCompletableFuture()`` to create these objects. 

```java
    import static rsp.state.UseState.readWrite;
    ...
    public static Component<ConfirmPanelState> confirmPanelComponent(String text) {
        return s -> div(attr("class", "panel"),
                         span(text),
                         buttonComponent("Ok").render(readWrite(() -> new ButtonState(), 
                                                                buttonState -> s.accept(new ConfimPanelState(true)))),
                         buttonComponent("Cancel").render(readWrite(() -> new ButtonState(), 
                                                                    buttonState -> s.accept(new ConfimPanelState(false))));
        
    }
    public static class ConfirmPanelState {
        public final boolean confirmed;
        public ConfirmPanelState(boolean confirmed) { this.confirmed = confirmed; }
    }
```

An application's top-level ``Component<S>`` is the root of its component tree.

### Routing

To resolve an initial application state from an HTTP request during the first rendering, create a function like that:

```java
    public CompletableFuture<State> route(HttpRequest request) {
        return route(request.path);
    }
    
    public CompletableFuture<State> route(Path path) {
        final Path.Matcher<State> m = path.matcher(CompletableFuture.completedFuture(error())) // a default match
                                          .when((name) -> true,                 // /{name}
                                                (name) -> db.getList(name).map(list -> State.of(list)))
                                          .when((name, id) -> isNumeric(id),    // /{name}/{id}
                                                (name, id) -> db.getOne(Long.parse(id)).map(instance -> State.of(instance)));
        
        return m.result;
    }
```

Provide a request routing function to the application's class ``App`` constructor.
The default request-to-state routing implementation returns an initial state for any incoming HTTP request.

In a kind of opposite way, the current application's state can be mapped to the browser's navigation bar path using another function,
which corresponds to another parameter of the ``App`` constructor.
 
```java
     public static Path state2path(State state) {
        //  /{name}/{id} or /{name}
        return state.details.map(details -> new Path(state.name, Long.toString(details.id))).or(new Path(state.name));
    }
```
The default state-to-path routing sets an empty path for any state.

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



   