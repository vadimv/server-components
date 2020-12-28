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

A self-hosted Java server process renders initial HTML markup with a 10Kb JavaScript client-side program. 

An application starts to listen to its events, like mouse click, which are streamed from browser to the server process. 
The application's code processes these events and updates its internal state, which may be represented in time as a sequence of immutable snapshots. 
A new state is rendered to a next virtual DOM tree. 
The difference between the previous and a new virtual DOM trees used to generate commands for the browser to synchronize the new server-side virtual DOM with actual client side HTML document.


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

  
### Events and State

Use ``rsp.dsl.Html.on(eventType, handler)`` method to register a handler to a browser event.

```java
    a("#", "Click me", on("click", ctx -> {
                System.out.println("Clicked!");    
            }))
```
In an RSP application a ``UseState<S>`` object provided as a parameter in an application or a component rendering code. 
An event handler usually should change the current state using ``UseState<S>.accept(S newState)`` method.

### Components

An RSP application may be is composed of components. A component is a Java class implementing ``Component<S>`` interface.
A parent component ``render()`` method invokes ``render()`` methods of its children components
providing an instance ``UseState<S>`` as an argument. 
In this way an application state is a composition of its components states.



### Routing

TBD

### Schedules and external events

TBD

### Application's configuration

TBD
   