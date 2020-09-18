# Reactive Sever Pages

Reactive Sever Pages (RSP) is a Java library for creating single page web applications and dynamic websites.

RSP is a simplified port of parts of Korolev Scala framework to Java.

How it works:
a browser event like click or form submit sent on websocket to the server where it updates the state resulting with a new virtual DOM.
The difference between the current and new virtual DOM trees calculated and sent back to the browser to update the page's presentation.

* Plain Java used for both sever and client side programming including a Java DSL for HTML
* No need for APIs like REST, GraphQL etc
* A page should load fast—it is bare HTML plus a 10K JavaScript file
* SEO friendly

## Usage

See the [TODOs example](https://github.com/vadimv/reactive-server-pages/blob/master/src/main/java/rsp/examples/JettyTodos.java) for Jetty.