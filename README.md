# Reactive Sever Pages

Reactive Sever Pages (RSP) enables creating single page web applications and dynamic websites in Java.

## Usage

This project requires Java 11. 

To build the project:

```

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
        );
```

There are a few utility methods for rendering a Java ``Stream<S>``, ``CompletableFuture<S>``, custom logic with if branching
and conditional rendering.

Rendering code uses its application state object provided as a state ``UseState<S>.get()`` or an external data source. 

  
### Events

Use ``rsp.dsl.Html.on(eventType, handler)`` method to register a handler for a browser event.

```java
    a("#", "Click me", on("click", ctx -> {
                System.out.println("Clicked!");    
            }))
```

### Components

An RSP application is composed of components. A component is a Java class implementing ``Component<S>`` interface.

In an RSP application a ``UseState<S>`` object provided as a parameter in an application or a component rendering code. 
An event handler usually should change the current state using ``UseState<S>.accept(S newState)`` method.

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

    public static Component<ConfimPanelState> confirmPanelComponent(String text) {
        return us -> div(attr("class", "panel"),
                         buttonComponent("Ok").render(useState(() -> new ButtonState(), 
                                                               buttonState -> us.accept(new ConfimPanelState(true)))),
                         buttonComponent("Cancel").render(useState(() -> new ButtonState(), 
                                                                   buttonState -> us.accept(new ConfimPanelState(false))));
        
    }
    public static class ConfimPanelState {
        public final boolean confirmed;
        public ConfimPanelState(boolean confirmed) { this.confirmed = confirmed; }
    }
```
An application is a top level ``Component<S>``.

### Routing

TBD

### Schedules and external events

TBD

### Application's configuration

TBD
   