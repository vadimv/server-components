# Server Components

A Java framework for building web UIs entirely on the server side, with real-time browser sync and native AI agent support.

Components run in a Java process, responding to browser events over WebSocket. An 11KB JavaScript client mirrors server-side DOM changes in the browser automatically. No frontend build toolchain. No JavaScript to write.

## Requirements

- Java 25+
- Maven 3.x

## Getting started

```bash
git clone https://github.com/vadimv/server-components.git
cd server-components
mvn install
```

Run the hello world example:

```bash
mvn exec:java -pl examples -Dexec.mainClass="rsp.app.HelloWorld"
# open http://localhost:8080
```

## Example apps

| App               | Main class                                                                           | Port | What it demonstrates                                 |
|-------------------|--------------------------------------------------------------------------------------|------|------------------------------------------------------|
| Hello world       | [`rsp.app.HelloWorld`](examples/src/main/java/rsp/app/HelloWorld.java)               | 8080 | Minimal component and server setup                   |
| Plain form        | [`rsp.app.PlainForm`](examples/src/main/java/rsp/app/PlainForm.java)                 | 8080 | Form handling with the HTML DSL                      |
| Counters          | [`rsp.app.counters.Counters`](examples/src/main/java/rsp/app/counters/Counters.java) | 8085 | Component composition, URL sync, state caching       |
| Game of Life      | [`rsp.app.gameoflife.Life`](examples/src/main/java/rsp/app/gameoflife/Life.java)     | 8082 | Stateful updates and scheduled server-side rendering |
| Todos             | [`rsp.app.todos.JettyTodos`](examples/src/main/java/rsp/app/todos/JettyTodos.java)   | 8080 | CRUD-style interactions with a single component      |
| Posts / CRUD demo | [`rsp.app.posts.CrudApp`](examples/src/main/java/rsp/app/posts/CrudApp.java)         | 8085 | Compositions, auth, AI agents, approval workflows    |

## How it works

```
Browser (11KB JS) ── WebSocket ── Java process
                                    ├── Component tree
                                    ├── Event loop
                                    └── DOM diffing
```

A component is a Java class with a state, a view function that maps state to HTML (expressed in a Java DSL), and event handlers that produce new state. The framework diffs the resulting DOM and pushes minimal changes to the browser.

```java
// Java HTML DSL
div(attr("class", "card"),
    h1(post.title()),
    p(post.body()),
    button(on("click", ctx -> handleEdit(post)), "Edit"))
```

The DSL maps one-to-one to HTML — if you know HTML, you can read it. And you don't need to write it by hand: coding AI agents produce this DSL fluently, since it's a direct structural mirror of the markup they already know.

State is immutable. Event handlers return new state, never mutate. The framework manages component lifecycle, context propagation between parent and child components, and cleanup.

## Compositions framework

For larger applications, the compositions module provides a higher-level structure:

- **Contracts** — typed UI units (list, edit, create) with declared capabilities
- **Groups** — hierarchical contract binding with navigation metadata
- **Scenes** — immutable snapshots of what's on screen (routed contract + companions + lazy overlays)
- **Layouts** — pluggable rendering strategies (sidebar, header, modal layers)
- **Router** — URL-to-contract mapping with path parameters

```java
final Group posts = new Group("Posts").description("Blog posts")
        .bind(PostsListContract.class,
              ctx -> new PostsListContract(ctx, postService),
              DefaultListView::new)
        .bind(PostEditContract.class,
              ctx -> new PostEditContract(ctx, postService),
              DefaultEditView::new);

final Router router = new Router()
        .route("/posts", PostsListContract.class)
        .route("/posts/:id", PostEditContract.class);

final DefaultLayout layout = new DefaultLayout()
        .leftSidebar(ExplorerContract.class)
        .rightSidebar(PromptContract.class);

final Composition app = new Composition(router, layout, posts);
```

## AI agent integration

Contracts expose their actions and metadata to AI agents. An agent can discover available UI operations, plan multi-step sequences, and execute them — subject to authorization.

- **Tool discovery** — agents enumerate contracts and their actions via the structure tree
- **Multi-step planning** — agents plan and execute sequences of UI actions
- **ABAC authorization** — attribute-based access control gates what agents can do
- **Approval workflows** — human-in-the-loop confirmation before agent actions
- **LLM backends** — Claude API, Ollama, or custom implementations

## AI-native development model

This project is intended to be used primarily from source.

**Using it:** Clone the repo. Use your coding AI agent to extend and adapt it for your needs. The codebase is designed to be legible to both humans and LLMs — small files, clear conventions, comprehensive test suite.

**Workflow:** The recommended path is to work directly from the repository rather than treat it as a black-box dependency.

**Contributing:** File issues for bugs and missing functionality. Pull requests are not accepted. Changes are integrated by an LLM agent on the project side.

## Auditable by design

This project aims to provide strong runtime supply-chain guarantees. The target architecture is zero third-party runtime dependencies outside the web-server layer.

Current status:

| Layer          | Dependencies                             | Notes                                                                                                           |
|----------------|------------------------------------------|-----------------------------------------------------------------------------------------------------------------|
| core           | json-simple                              | targeted for internalization                                                                                    |
| js-client      | none                                     | ES6 source compiled to 11KB by Closure Compiler at build time                                                   |
| compositions   | none (beyond core)                       |                                                                                                                 |
| web server     | Jetty 12, Jakarta Servlet/WebSocket APIs | abstracted behind an API and is intended to be replaceable with other implementations, such as Tomcat or Netty. |
| html-converter | jsoup                                    | utility module, not required at runtime                                                                         |

## Project structure

```
server-components/
├── core/               Component model, HTML DSL, page lifecycle, event loop
├── js-client/          Browser-side ES6 (~32KB source → 11KB minified)
├── servlet-api/        Jakarta Servlet/WebSocket abstraction
├── jetty-web-server/   Embedded Jetty web server
├── html-converter/     HTML → Java DSL conversion utility
├── compositions/       Contracts, scenes, layouts, routing, AI agents, ABAC
└── examples/           Demo apps
    ├── HelloWorld       Minimal component
    ├── Counters         Component composition, URL sync, state caching
    ├── GameOfLife       State update loops, grid rendering
    ├── Todos            CRUD operations
    └── Posts (CrudApp)  Full CRUD + AI agents + authorization
```
