A Java server-side rendering (SSR) web framework.

## Project's goal

A 'vertically-integrated' solution for building responsive near-realtime web UIs in Java with minimal dependencies.

## Core concepts

- A UI is made of nested components: every component contains HTML markup expressed in Java DSL and/or other components.
- Components can be developed and tested independently and are composable).
- A component's life-cycle is managed by the framework.
- The framework provides a standard way for components to share context and send change notifications to each other.
- Components run on the server-side in a Java process, responding to events. 
- Client-side contains an 11kB JS code which automatically updates its browser view to mirror the DOM changes on the server.
- This project is a foundation of a Java-centric web UI technology stack targeting to self-sufficiency, conciseness and possibility to be understood top to bottom by a human. 
