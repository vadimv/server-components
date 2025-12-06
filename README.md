A Java web Server Side Rendering framework.

## Project's goal

Provide a 'vertically-integrated' solution for building responsive near-realtime web UIs in Java with minimal dependencies.

## Core concepts

- A UI is made of nested components: every component contains HTML markup expressed in Java DSL and/or other components.
- Components can be developed and tested independently and are composable).
- A component's life-cycle is managed by the framework.
- The framework provides a standard way for components to share context and send change notifications.
- Components rendering runs on the server-side in a Java process, handling events, with automatic UI update on the client-side.
