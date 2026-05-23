# Java UI for Admin Panels and realtime web apps

This is a pure-Java toolkit for stateful, server-rendered live applications like admin interfaces and internal tools.

**[Live Admin Panel Demo](https://server-components.onrender.com)**

No need for writing JavaScript / JSX and rely on thousands of transitive npm dependencies.  
No need for REST endpoints, frameworks like React, frontend build steps and managing client-side state.

[![Admin-UI-demo](https://github.com/user-attachments/assets/ce5f6944-7bd2-4a3c-9afe-cfa72799074f)](https://server-components.onrender.com)

- Server-Side-Rendering (SSR) architecture like in Elixir Phoenix LiveView, Blazor Server but for Java.
- Software Engineers: build UIs in plain modern Java with no annotations and implicit control flows; components can be tested in isolation.
- The Java HTML DSL is linear and composable. That makes it easy for coding LLMs (like Claude or Codex) to generate valid DSL HTML and a component tree.
- At runtime, the AI agent natively understands the application's structure, navigates the UI, and queues up the actions with Human-in-the-loop for approvals.
- This project aims for zero third-party runtime dependencies.

## What does the code look like?

```java
import rsp.component.View;
import rsp.component.definitions.InitialStateComponent;
import rsp.jetty.WebServer;

import static rsp.dsl.Html.*;

public final class Counter {
   static void main(final String[] args) {
      final ComponentView<Integer> view = newState -> state ->
              html(
                      body(
                              h1("Current count: " + state),
                              button(on("click", _ -> newState.setState(state + 1)),
                                     text("Increment"))
                      )
              );

      final var server = new WebServer(8080, _ -> new InitialStateComponent<>(0, view));
      server.start();
      server.join();
   }
}
```
This is a complete runnable interactive web application. For more examples check the [examples/README.md](examples/README.md).

## Getting started

**Want to skip the local setup?** [Play with the live demo here](https://server-components.onrender.com).

1. Prerequisites
   - Java 25 -- virtual threads, sealed interfaces and pattern matching
   - Maven 3.9

2. Clone the repository

```bash
git clone https://github.com/vadimv/server-components.git
cd server-components
```

3. Build the project

```bash
mvn install
```

4. Run the included admin example

```bash
mvn exec:java -pl examples -Dexec.mainClass="rsp.app.posts.CrudApp"
```

Open `http://localhost:8085`, click `Sign in`, and explore the Posts/Comments admin app.

By default, `CrudApp` uses `RegexAgentService`, a deterministic regex-based agent stub included in the repository. It is meant for demos and local validation, so you can try the AI workflow without setting up Anthropic, Ollama, or any other LLM backend.

5. Try a few prompts

- `open comments`
- `go to page 2`
- `open comments and go to page 2 and select all`

6. Generate your own admin app

Once you've run the example, open your favorite AI coding assistant in the project root and paste a prompt like this:

*Read examples/src/main/java/rsp/app/posts/CrudApp.java.
Generate a similar realtime admin tool for managing Employees and Departments.
Create mock services and a new EmployeeAdminApp.java using the same routing, composition, and AI-agent integration patterns.*

7. Upgrade to a full LLM agent

When you're ready, run `CrudApp` with `-Dai.agent=claude` or `-Dai.agent=ollama` and connect your preferred model backend.

## Docs

- [Core](core/README.md)
- [Compositions framework](compositions/README.md)
- [Browser-side integration](compositions/CLIENT_INTEGRATION.md)
- [Examples](examples/README.md)
