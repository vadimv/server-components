# Java UI for Admin Panels and Realtime Web Apps

Welcome! This is a server-side Java UI toolkit for admin panels, internal tools, and live
web applications. Components keep state and run event handlers on the server;
the browser receives initial HTML and small DOM updates over WebSocket.

[![Admin UI demo](https://github.com/user-attachments/assets/ce5f6944-7bd2-4a3c-9afe-cfa72799074f)](https://server-components.onrender.com)

- plain Java with constructor injection and no annotation-driven lifecycle;
- typed component state, intents, context, and events;
- no third-party runtime dependencies in the framework modules;
- optional compositions, schema, authorization, dashboard, and agent modules.

## Smallest Interactive Application

```java
import rsp.component.ComponentView;
import rsp.component.definitions.LocalStateComponent;
import rsp.http.WebServer;

import static rsp.dsl.Html.*;

public final class Counter {
    private enum CounterIntent { INCREMENT }

    static void main(String[] args) {
        ComponentView<Integer, CounterIntent> view = intents -> state ->
                html(body(
                        h1("Current count: " + state),
                        button(on("click", _ -> intents.dispatch(CounterIntent.INCREMENT)),
                               text("Increment"))));

        WebServer server = new WebServer(8080, _ ->
                new LocalStateComponent<>((_, _) -> 0, view,
                        (state, intent) -> state + 1));
        server.start();
        server.join();
    }
}
```

The server renders the first page, the browser opens a WebSocket, and subsequent
events run Java handlers that produce targeted DOM patches. Application state
does not need to be duplicated in a JavaScript frontend.

## Try The Repository

Requirements: Java 25 and Maven 3.8.7 or newer.

```bash
git clone https://github.com/vadimv/server-components.git
cd server-components
mvn clean install
mvn exec:java -pl examples -Dexec.mainClass=rsp.app.posts.CrudApp
```

Open <http://localhost:8085> and select **Sign in**. The default agent is a
deterministic local stub and requires no API key.

## Documentation

- [Documentation home](docs/index.md)
- [Getting started](docs/getting-started.md)
- [Examples](docs/examples.md)
- [Core runtime and component concepts](docs/concepts/core.md)
- [Compositions and routed application concepts](docs/concepts/compositions.md)
- [Module map](docs/reference/module-map.md)
