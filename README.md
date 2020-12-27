# Reactive Sever Pages

Reactive Sever Pages (RSP) enables creating single page web applications and dynamic websites in Java.

## Applications examples

See the [TODOs](https://github.com/vadimv/reactive-server-pages/blob/master/src/main/java/rsp/examples/todos/JettyTodos.java) example,
[Hacker News client](https://github.com/vadimv/reactive-server-pages/blob/master/src/main/java/rsp/examples/hnapi/JettyHn.java) example
and [Tetris](https://github.com/vadimv/reactive-server-pages/blob/master/src/main/java/rsp/examples/tetris/Tetris.java) example.

## How it works

A self-hosted e.g. Jetty-based Java server process renders initial HTML markup and sends a 10Kb JavaScript 
client-side program with it. 

After that, the process begins to listen to the apps' client side events, like 
button click, slider move, key press, and according to an application's logic updates its internal state. An application's state is represented as a series of immutable snapshots. 
A change of the state leads to generation of the updated virtual DOM tree. 

The difference between current and next virtual DOM
trees calculated and used to generate the commands to be sent to the browser to synchronize the new server-side virtual DOM with actual client side HTML document.

###HTML markup Java DSL

RSPs use Java DSL for representing HTML tags and attributes.

TODO code example

### Events and State

### Components

### Routing

### Schedules and external events

### Application's configuration
   