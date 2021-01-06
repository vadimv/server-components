The Reactive Sever Pages (RSP) project enables creating single page web applications and dynamic websites in Java.

## Usage

This project requires Java 11+. 

To build the project:

```shell script

$ mvn clean package

```

See the [TODOs](https://github.com/vadimv/reactive-server-pages/blob/master/src/main/java/rsp/examples/todos/JettyTodos.java) example,
[Hacker News client](https://github.com/vadimv/reactive-server-pages/blob/master/src/main/java/rsp/examples/hnapi/JettyHn.java)
and [Tetris](https://github.com/vadimv/reactive-server-pages/blob/master/src/main/java/rsp/examples/tetris/Tetris.java) examples.

## How it works

On an HTTP request, a self-hosted Java webserver process renders initial HTML markup with a 10Kb JavaScript client-side program. 
After the page is loaded the client program establishes a web socket connection. 

A live page session is created on the server-side, which starts to listen to the browser events, like a mouse click. 
The application handles these events and updates its internal state, generating a sequence of immutable snapshots. 
Every new state snapshot results in its corresponding virtual DOM tree through the application's rendering. 
The difference between the current and a new virtual DOM trees is used to evoke commands like creating or delete an element
or an attribute. These commands are sent to the browser via a web socket.
On the client-side, these commands used to adjust the actual page's HTML document to the new server-side virtual DOM.


### HTML markup Java DSL

RSP uses a Java DSL for representing HTML tags and attributes.

For example, a fragment of HTML like this

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
    html(
          body(
               h1("This is a heading"),
               div(attr("class", "par"), p("This is a paragraph"))
              ) 
        )
```

There are a few utility methods for rendering a Java ``Stream<S>``, ``CompletableFuture<S>``, custom logic with if branching
and conditional rendering.

Rendering code uses its application state object provided as a state ``UseState<S>.get()`` or an external data source. 

```java
    ul(of(us.get().items.stream().map(item -> li(item.name))))
```

```java
    final Function<Long, CompletableFuture<String>> service = userDetailsService(); 
    ...
    // let's consider that at this moment we know the current user's Id
    div(of(service.apply(us.get().user.id).map(str -> text(str))))
```

```java
    when(us.get().showLabel, span("This is a label"))
```

  
### Events

Use ``rsp.dsl.Html.on(eventType, handler)`` method to register a handler for a browser event.

```java
    a("#", "Click me", on("click", ctx -> {
                System.out.println("Clicked!");    
            }))
```
An ``EventContext`` object, provided as a parameter to an event's handler has a number of useful methods.  
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
  
Another ``EventContext`` method enables access to the event's object.

```java
    form(on("submit", ctx -> {
            System.out.println(ctx.eventObject().apply("val").orElseThrow(() -> new IllegalStateException()));
         }),
        input(attr("type", "text"), attr("name", "val")),
        input(attr("type", "button"), attr("value", "Submit"))
    )
```


### Components

An RSP application is composed of components. A component is a Java class implementing ``Component<S>`` interface.

An event handler usually should provide a new state snapshot object using ``UseState<S>.accept(S newState)`` method.

```java
    public static Component<ButtonState> buttonComponent(String text) {
        return us -> input(attr("type", "button"),
                           attr("class", "button"),     
                           on("click", ctx -> us.accept(new ButtonState())),
                           text(text));
        
    }
    public static class ButtonState {}
```

A parent component ``render()`` method invokes ``render()`` methods of its children components
providing an instance of the ``UseState<S>`` class as an argument. 

```java
    import static rsp.state.UseState.useState;
    ...
    public static Component<ConfirmPanelState> confirmPanelComponent(String text) {
        return us -> div(attr("class", "panel"),
                         span(text),
                         buttonComponent("Ok").render(useState(() -> new ButtonState(), 
                                                               buttonState -> us.accept(new ConfimPanelState(true)))),
                         buttonComponent("Cancel").render(useState(() -> new ButtonState(), 
                                                                   buttonState -> us.accept(new ConfimPanelState(false))));
        
    }
    public static class ConfirmPanelState {
        public final boolean confirmed;
        public ConfirmPanelState(boolean confirmed) { this.confirmed = confirmed; }
    }
```
An application is a top-level ``Component<S>``.

### Routing

Initial application's state is resolved during first rendering on by a specific function,
 provided as a parameter to the ``App`` constructor.

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
The default request-to-state routing implementation just provides an initial state for all requests.

Current application's state can be mapped to the browser's navigation bar path using another specific function,
also provided as a parameter of the ``App`` constructor.
 
```java
     public static Path state2path(State state) {
        return state.details.map(details -> new Path(state.name, Long.toString(details.id))).or(new Path(state.name));
    }
```
The default state-to-path routing sets an empty path for any state.

### Schedules and external events

The ``EventContext.schedule()`` and ``EventContext.scheduleAtFixedRate()`` 
methods allows submitting of a delayed or periodic action that can be cancelled. 
These actions will be executed in a thread from the internal thread pool.

TBD

### Application's configuration

TBD


### Logging

By default, internally the project uses a console logger. To change log level of the default console logger, 
set ``rsp.log.level`` system property, for example ``-Drsp.log.level=trace``.

To use a Slf4j logger instead of the default console logger, provide the ``Slf4jLogReporting`` logger implementation to
the ``AppConfig`` application configuration. 

To enable client-side detailed diagnostic data exchange logging, enter in the browser's console:

```javascript
  RSP.setProtocolDebugEnabled(true)
```



   