# Examples

Runnable demo applications, ordered from simplest to most complete. Each entry lists the entry-point class, the port the server binds to, and the concepts the example introduces.

Follow [getting started](getting-started.md) to build the repository once. Then
run any entry point from the repository root:

```bash
mvn exec:java -pl examples -Dexec.mainClass=<fully.qualified.MainClass>
```

Or from your IDE, by running the `main` method of the entry-point class.

## Catalog

### REST-only — routing, JSON, and the generic JDK transport

- Entry point: [RestHello.java](../examples/src/main/java/rsp/app/rest/RestHello.java)
- URL: <http://localhost:8080/api/hello/Alice>
- Demonstrates: a UI-independent `HttpRouter`, decoded path parameters,
  `JsonHttp` responses and request validation, typed JSON errors, and direct
  hosting through `JdkWebServer`.

### 1. Counter — minimal stateful component
- Entry point: [Counter.java](../examples/src/main/java/rsp/app/Counter.java)
- URL: <http://localhost:8080>
- Demonstrates: the smallest possible app — a `LocalStateComponent<Integer, CounterIntent>`, an intent-only `ComponentView<Integer, CounterIntent>`, and a reducer that owns the count cache.

### 2. PlainForm — request-driven page with GET/POST
- Entry point: [PlainForm.java](../examples/src/main/java/rsp/app/PlainForm.java)
- URL: <http://localhost:8080>
- Demonstrates: a generic `/api/health` route and UI pages on one `WebServer`, branching the page state on the HTTP method, sealed interface state (`EmptyName` / `FullName`), and posting a classic HTML `<form>` whose query parameters drive the next render.

### 3. JettyTodos — TODO tracker with form submit
- Entry point: [JettyTodos.java](../examples/src/main/java/rsp/app/todos/JettyTodos.java)
- URL: <http://localhost:8080>
- Demonstrates: list rendering with `of(stream...)`, an `ElementRef` to read the text input's `value` on submit, `on("submit", true, ...)`, and updating immutable state arrays.

### 4. Life — Conway's Game of Life
- Entry point: [Life.java](../examples/src/main/java/rsp/app/gameoflife/Life.java)
- URL: <http://localhost:8082>
- Demonstrates: large grid rendering, click-to-toggle cells, control buttons (Start / Stop / Clear / Random), and the `onUpdated` / `onUnmounted` lifecycle hooks driving a `ScheduledExecutorService` to advance generations. Also shows serving CSS via `StaticResources`.

### 5. Counters — multiple components synced to the URL
- Entry point: [Counters.java](../examples/src/main/java/rsp/app/counters/Counters.java)
- URL: <http://localhost:8085/16/-1?c4=27>
- Demonstrates: a tree of components coordinated by [CountersMainComponent](../examples/src/main/java/rsp/app/counters/CountersMainComponent.java), extending `AddressBarSyncComponent` to map path elements and query parameters to context keys:
  - [ContextCounterComponent](../examples/src/main/java/rsp/app/counters/ContextCounterComponent.java) — `ContextStateComponent<Integer>` synced to a URL path element or query parameter.
  - [CachedCounterComponent](../examples/src/main/java/rsp/app/counters/CachedCounterComponent.java) — `StoredStateComponent<Integer>` whose state survives unmount via a shared `ConcurrentHashMap`.
  - [HideableCounterComponent](../examples/src/main/java/rsp/app/counters/HideableCounterComponent.java) — conditional rendering with `when(state, ...)` toggled by a checkbox.
  - [CountersView](../examples/src/main/java/rsp/app/counters/CountersView.java) — a single reusable view shared by all three counter types.
  - [CountersAppComponent](../examples/src/main/java/rsp/app/counters/CountersAppComponent.java) — top-level routing between the counters page and a 404 page.

### 6. CrudApp — full admin panel with AI agent
- Entry point: [CrudApp.java](../examples/src/main/java/rsp/app/posts/CrudApp.java)
- URL: <http://localhost:8085>
- Demonstrates the end-to-end `compositions` stack:
  - **Routing** — immutable `RouteTable<BlockTarget>` mapping `/posts`, `/posts/new`, `/posts/{id}`, `/comments`, and `/comments/{id}` to blocks.
  - **Blocks + views** — direct state-owning list ([PostsListBlock](../examples/src/main/java/rsp/app/posts/components/PostsListBlock.java), [CommentsListBlock](../examples/src/main/java/rsp/app/posts/components/CommentsListBlock.java)) and edit/create form components ([PostEditBlock](../examples/src/main/java/rsp/app/posts/components/PostEditBlock.java), [PostCreateBlock](../examples/src/main/java/rsp/app/posts/components/PostCreateBlock.java), [CommentEditBlock](../examples/src/main/java/rsp/app/posts/components/CommentEditBlock.java), [CommentCreateBlock](../examples/src/main/java/rsp/app/posts/components/CommentCreateBlock.java)). `DefaultListView` is a complete schema-driven grid with exact counts, bounded first/previous/next/last pagination, page-size selection, keyed sorting, global search, per-column filters, guarded row and bulk actions, stable empty/error states, query-preserving edit links, related-comments navigation, responsive overflow, and accessible table metadata. `DefaultFormView` renders semantic, typed, accessible create/edit forms with dirty and busy states, field-level persistence feedback, a post ID/label relationship selector, not-found handling, and capability-gated actions. Destructive and dirty-discard browser actions use accessible native confirmation dialogs. Both views dispatch typed intents and share [CrudSchemas](../examples/src/main/java/rsp/app/posts/components/CrudSchemas.java).
  - **Groups** — nested `Group("Admin") → Group("Posts") / Group("Comments")`; the tree drives the [ExplorerBlock](../extensions/ui-shell/src/main/java/rsp/compositions/shell/ExplorerBlock.java) sidebar menu.
  - **Telemetry dashboard** — [DemoDashboards](../examples/src/main/java/rsp/app/posts/components/DemoDashboards.java) declares an immutable trend and log panel, while [DemoTelemetry](../examples/src/main/java/rsp/app/posts/components/DemoTelemetry.java) adapts the live services separately. Scheduler-backed prompt, comment-rate, and log services are registered in `ApplicationContext`, start once with the server, and stop in reverse order during shutdown.
  - **Layout** — `DefaultLayout` with left sidebar (Explorer), right sidebar (Prompt), header, and a placement policy mapping forms inline and approvals to modals.
  - **Auth** — a separate `Composition` renders `/auth/login`, while `SimpleAuthProvider` authenticates before component creation and owns server-side sign-in/sign-out cookies and redirects.
  - **AI agent** — [PromptBlock](../extensions/agent-ui/src/main/java/rsp/compositions/agentui/PromptBlock.java) talks to an `AgentService`, selectable via `-Dai.agent=regex|claude|ollama`. Backed by ABAC authorization (`AccessPolicy`, `Authorization`) and human-in-the-loop approvals via `ApprovalSpawner` + `DelegationApprovalBlock`.
  - **Domain** — [PostService](../examples/src/main/java/rsp/app/posts/services/PostService.java) and [CommentService](../examples/src/main/java/rsp/app/posts/services/CommentService.java) return typed form outcomes, enforce the comment-to-post relationship, and demonstrate cascading comment deletion when a post is removed. Agent parsing lives in [RegexAgentService](../examples/src/main/java/rsp/app/posts/services/RegexAgentService.java); records live in [entities/](../examples/src/main/java/rsp/app/posts/entities/).

  Selecting the agent backend:
  ```bash
  # default — deterministic regex stub, no external calls
  mvn exec:java -pl examples -Dexec.mainClass=rsp.app.posts.CrudApp

  # Claude (requires ANTHROPIC_API_KEY)
  mvn exec:java -pl examples -Dexec.mainClass=rsp.app.posts.CrudApp -Dai.agent=claude

  # local Ollama
  mvn exec:java -pl examples -Dexec.mainClass=rsp.app.posts.CrudApp -Dai.agent=ollama
  ```

## Concept coverage

| Concept                                                  | Counter | PlainForm | JettyTodos | Life | Counters | CrudApp |
|----------------------------------------------------------|:-------:|:---------:|:----------:|:----:|:--------:|:-------:|
| `InitialStateComponent`                                  |    +    |           |     +      |      |          |         |
| Custom `Component<S>` subclass                           |         |     +     |            |  +   |    +     |    +    |
| Sealed-interface state                                   |         |     +     |            |      |    +     |         |
| `ElementRef` / form submit                               |         |           |     +      |      |          |    +    |
| HTTP route + UI page on one server                      |         |     +     |            |      |          |    +    |
| HTTP method / query-param branching                      |         |     +     |            |      |          |         |
| Lifecycle hooks (`onUpdated` / `onUnmounted`)            |         |           |            |  +   |          |    +    |
| URL ↔ state sync (`AddressBarSyncComponent`)             |         |           |            |      |    +     |         |
| Persistent state across unmount (`StoredStateComponent`) |         |           |            |      |    +     |         |
| Conditional rendering (`when(...)`)                      |         |           |     +      |  +   |    +     |    +    |
| Static resources (`StaticResources`)                     |         |           |            |  +   |    +     |    +    |
| Routing (`RouteTable` + blocks)                       |         |           |            |      |          |    +    |
| Layout + composition + groups                            |         |           |            |      |          |    +    |
| Schema-driven data grid                                  |         |           |            |      |          |    +    |
| Auth composition                                         |         |           |            |      |          |    +    |
| AI agent + ABAC + HITL approval                          |         |           |            |      |          |    +    |
