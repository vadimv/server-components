# Reactive Server Pages
[![javadoc](https://javadoc.io/badge2/io.github.vadimv/rsp/javadoc.svg)](https://javadoc.io/doc/io.github.vadimv/rsp)

The Reactive Server Pages (RSP) project enables creating single page applications and dynamic websites in Java

RSP UI lives on the server-side.
The browser sends events to the server and updates the presentation to the incoming DOM change commands.

## Motivation

A common approach to build web UI today is to break it to the server and client-side and connect them with some kind of API using some protocol. 
This makes a UI application effectively a two-part distributed system and therefore introduces a lot of inherited complexity.
Often, these two sides even use different programming languages and development stacks, dependency management, and build tools.
This adds even more complexity, but even without it, it is already complicated. Care should be taken separately about the server-side, API, and the client-side,
any change made on the client-side needs to be reflected on the API and the server-side.

The Reactive Server Pages approach pursues simplicity for a Java developer by abstracting the browser-side whenever possible.
The development process feels more like it may be for a desktop application UI in reactive style with HTML and CSS or
creating a React application with direct access to its backend data. 

The implicit pluses are:
- fast initial page load no matter of the application's size;
- your code always stays on your server;
- SEO-friendly out of the box.
    
The obstacles to deal with:
- may not be a good fit for use cases requiring very low response time, heavy animations, etc;
- latency between a client and the server should be low enough to ensure a good user experience;
- more server memory and CPU resources may be required on the server;
- as for a stateful app, for scalability some kind of sticky session management required;
- a question of how to integrate an RSP with existing JavaScript and CSS codebase needs to be addressed. 

## Usage

This project requires Java 11 or newer. 

Maven dependency:
```xml
    <dependency>
        <groupId>io.github.vadimv</groupId>
        <artifactId>rsp</artifactId>
        <version>0.3</version>
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

![](https://github.com/vadimv/rsp-tetris/blob/main/src/main/java/rsp/tetris/tetris_sample.gif)

## How it works

On an HTTP request, a self-hosted Java webserver process renders and sends an initial HTML page markup with a 10Kb JavaScript client-side program. 
Later the browser loads the page, and the client program establishes a WebSocket connection with the server.
 
As a result, the server creates a live page session and starts to listen to the future browser events, like mouse clicks.  
The application logic handles these events and updates the application's internal state. On a state change,
the server generates a new virtual DOM tree invoking the rendering code with this new state snapshot as an argument.
The RSP library code calculates the difference between these new and current virtual DOM trees and uses it to generate the browser's commands. Next, the server sends these commands to the client via the web socket. 

Finally, The browser's JavaScript program adjusts the actual HTML document to the new server-side virtual DOM.

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
    final RefDefinition inputRef = createRef();
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
    button(attr("type", "button"),
           text("Start"),
           on("click", c -> c.scheduleAtFixedRate(() -> System.out.println("Timer event")), "timer0", 0, 1, TimeUnit.SECONDS))),
    button(attr("type", "button"),
               text("Stop"),
               on("click", c -> c.cancelSchedule("timer0")))
```

### Application configuration

See the ``rsp.AppConfig`` class for details.

### Logging

By default, internally, the project uses a console logger.
Set ``rsp.log.level`` system property to change its log level, for example ``-Drsp.log.level=trace``.

To use an Slf4j logger instead of the default console logger, provide the ``Slf4jLogReporting`` logger implementation to
the ``AppConfig`` application configuration. 

To enable client-side detailed diagnostic data exchange logging, enter in the browser's console:




   