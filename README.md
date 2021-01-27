The Reactive Server Pages (RSP) project enables creating single page web applications and dynamic websites in Java.

## Usage

This project requires Java 11 or more recent. 

Maven dependency:
```xml
    <dependency>
        <groupId>io.github.vadimv</groupId>
        <artifactId>rsp</artifactId>
        <version>0.1</version>
    </dependency>
```

To build the project from the sources:

```shell script

$ mvn clean package

```

See the [TODOs](https://github.com/vadimv/reactive-server-pages/blob/master/src/main/java/rsp/examples/todos/JettyTodos.java) example,
[Hacker News client](https://github.com/vadimv/reactive-server-pages/blob/master/src/main/java/rsp/examples/hnapi/JettyHn.java)
and [Tetris](https://github.com/vadimv/reactive-server-pages/blob/master/src/main/java/rsp/examples/tetris/Tetris.java) examples.

## How it works

On an HTTP request, a self-hosted Java webserver process renders and sends an initial HTML page markup with a 10Kb JavaScript client-side program. 
Later the browser loads the page, and the client program establishes a web socket connection with the server.
 
As a result, the server creates a live page session and starts to listen to the future browser events, like mouse clicks.  
The application logic handles these events and updates the application's internal state. On a state change,
the server generates a new virtual DOM tree invoking the rendering code with this new state snapshot as an argument.
The RSP library code calculates the difference between these new and current virtual DOM trees and uses it to generate the browser's commands. Next, the server sends these commands to the client via the web socket. 

Finally, The browser's JavaScript program adjusts the actual HTML document to the new server-side virtual DOM.

### HTML markup Java DSL

Use the Java DSL for defining an HTML page markup.
For example, a fragment:

```html
 <html>    
    <body>
      <h1>This is a heading</h1>
      <div class="par">
          <p>This is a paragraph</p>
      </div>
    </body>
</html> 
```

should be written in Java code as

```java
    import static rsp.dsl.Html.*;
    ...
    html(
          body(
               h1("This is a heading"),
               div(attr("class", "par"), p("This is a paragraph"))
              ) 
        )
```

Access the current application state reading a ``UseState<S>.get()`` object:  

```java
    ul(of(us.get().items.stream().map(item -> li(item.name))))
```

or some use external data source:
```java
    final Function<Long, CompletableFuture<String>> service = userDetailsService(); 
    ...
    // let's consider that at this moment we know the current user's Id
    div(of(service.apply(us.get().user.id).map(str -> text(str))))
```
There are a few utility methods for rendering a Java ``Stream<S>``, ``CompletableFuture<S>``, for addition of custom logic with if branching
and conditional rendering.

This code fragment demonstrates an example of conditional rendering.
Here, the ``span`` element will be visible or not depending on a boolean field of the state object:
```java
    when(us.get().showLabel, span("This is a label"))
```

### Events

Register a handler for a browser event using the ``rsp.dsl.Html.on(eventType, handler)`` method.

```java
    a("#", "Click me", on("click", ctx -> {
                System.out.println("Clicked!");    
            }))
```
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
  
Another ``EventContext`` method enables reading the event's object:

```java
    form(on("submit", ctx -> {
            // Prints the submitted form's input field value
            System.out.println(ctx.eventObject().apply("val").orElseThrow(() -> new IllegalStateException()));
         }),
        input(attr("type", "text"), attr("name", "val")),
        input(attr("type", "button"), attr("value", "Submit"))
    )
```

### Components

An RSP application is composed of components. A component is a Java class implementing ``Component<S>`` interface.

An event handler's code usually provides a new state snapshot object by the ``UseState<S>.accept(S newState)`` method.

```java
    public static Component<ButtonState> buttonComponent(String text) {
        return us -> input(attr("type", "button"),
                           attr("class", "button"),     
                           on("click", ctx -> us.accept(new ButtonState())),
                           text(text));
        
    }
    public static class ButtonState {}
```

A component's ``render()`` method invokes ``render()`` methods of its descendant components
with an instance of the ``UseState<S>`` class as an argument. 

```java
    import static rsp.state.UseState.readWrite;
    ...
    public static Component<ConfirmPanelState> confirmPanelComponent(String text) {
        return us -> div(attr("class", "panel"),
                         span(text),
                         buttonComponent("Ok").render(readWrite(() -> new ButtonState(), 
                                                                buttonState -> us.accept(new ConfimPanelState(true)))),
                         buttonComponent("Cancel").render(readWrite(() -> new ButtonState(), 
                                                                    buttonState -> us.accept(new ConfimPanelState(false))));
        
    }
    public static class ConfirmPanelState {
        public final boolean confirmed;
        public ConfirmPanelState(boolean confirmed) { this.confirmed = confirmed; }
    }
```
An application's top-level ``Component<S>`` is the root of its component tree.

### Routing

To resolve an initial application state from a HTTP request during the first rendering
create a function like that:

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

Provide this function to the application's class ``App`` constructor.
The default request-to-state routing implementation just provides an initial state for any incoming HTTP request.

In a kind of opposite way, the current application's state can be mapped to the browser's navigation bar path using another function,
also provided as a parameter of the ``App`` constructor.
 
```java
     public static Path state2path(State state) {
        return state.details.map(details -> new Path(state.name, Long.toString(details.id))).or(new Path(state.name));
    }
```
The default state-to-path routing sets an empty path for any state.

### Schedules

The ``EventContext.schedule()`` and ``EventContext.scheduleAtFixedRate()`` 
methods allow submitting of a delayed or periodic action that can be cancelled. 
These actions will be executed in a thread from the internal thread pool.

### Application creation and configuration

See the ``rsp.App`` and ``rsp.AppConfig`` classes for details.

### Logging

By default, internally the project uses a console logger. To change log level of the default console logger, 
set ``rsp.log.level`` system property, for example ``-Drsp.log.level=trace``.

To use a Slf4j logger instead of the default console logger, provide the ``Slf4jLogReporting`` logger implementation to
the ``AppConfig`` application configuration. 

To enable client-side detailed diagnostic data exchange logging, enter in the browser's console:

```javascript
  RSP.setProtocolDebugEnabled(true)
```



   