# UI Toolkit for Admin Panels and Enterprise Tools

Build AI-controlled admin interfaces and internal tools in modern Java, without writing JavaScript.

This is a pure-Java toolkit for stateful, server-driven back-office apps. It removes REST glue, frontend build steps, and most UI boilerplate while allowing AI agents to understand your application's structure out of the box.

## Turn workflows into sentences

<img width="1636" height="1029" alt="CrudApp-Screenshot-2" src="https://github.com/user-attachments/assets/ce5f6944-7bd2-4a3c-9afe-cfa72799074f" />

Prompt: "Open comments, go to page two, and select all items."

The AI agent natively understands your application's structure, navigates the UI, and queues up the exact actions while keeping the human in the loop for final approval.

## Why it's different

- Security & Supply Chain: Eliminate thousands of transitive npm dependencies. The toolkit is self-contained and distributed as source.
- Developer Productivity: Java developers build internal tools in plain Java — without writing web-service controllers, managing frontend state, or depending on JavaScript frameworks like React.
- AI: The Java HTML DSL is linear, component-based, and composable, with no annotations or implicit control flow. That makes it easy for LLMs (like Claude or GPT) to generate valid UI code without "hallucinating" state-management bugs, and lets AI agents navigate the running app from the same structure.

## What does the code look like?

```java
import rsp.component.View;
import rsp.component.definitions.InitialStateComponent;
import rsp.jetty.WebServer;

import static rsp.dsl.Html.*;

public final class HelloWorld {
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

## Getting started

1. Prerequisites
   - Java 25+
   - Maven 3.9+

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
- `search all posts with id < 2`

6. Generate your own admin app

Once you've run the example, open your favorite AI coding assistant in the project root and paste a prompt like this:

```
Read examples/src/main/java/rsp/app/posts/CrudApp.java.
Generate a similar real-time admin tool for managing Employees and Departments.
Create mock services and a new EmployeeAdminApp.java using the same routing, composition, and AI-agent integration patterns.
```

7. Upgrade to a full LLM agent

When you're ready, run `CrudApp` with `-Dai.agent=claude` or `-Dai.agent=ollama` and connect your preferred model backend.

## Auditable by design

This project aims to provide strong runtime supply-chain guarantees. The target architecture is zero third-party runtime dependencies outside the web-server layer.
