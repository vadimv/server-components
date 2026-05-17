# Examples

Runnable demo applications, ordered from simplest to most complete. Each entry lists the entry-point class, the port the server binds to, and the concepts the example introduces.

Run any example with Maven. First build the project once from the repository root, then run from the `examples/` directory:

```bash
mvn install -DskipTests          # one-time, from the repository root
cd examples
mvn exec:java -Dexec.mainClass=<fully.qualified.MainClass>
```

Or from your IDE, by running the `main` method of the entry-point class.

## Catalog

### 1. Counter — minimal stateful component
- Entry point: [Counter.java](src/main/java/rsp/app/Counter.java)
- URL: <http://localhost:8080>
- Demonstrates: the smallest possible app — a single `InitialStateComponent<Integer>`, a `ComponentView` lambda, and a click handler that calls `newState.setState(...)`.

### 2. PlainForm — request-driven page with GET/POST
- Entry point: [PlainForm.java](src/main/java/rsp/app/PlainForm.java)
- URL: <http://localhost:8080>
- Demonstrates: branching the initial state on the HTTP method, sealed interface state (`EmptyName` / `FullName`), and posting a classic HTML `<form>` whose query parameters drive the next render.

### 3. JettyTodos — TODO tracker with form submit
- Entry point: [JettyTodos.java](src/main/java/rsp/app/todos/JettyTodos.java)
- URL: <http://localhost:8080>
- Demonstrates: list rendering with `of(stream...)`, an `ElementRef` to read the text input's `value` on submit, `on("submit", true, ...)`, and updating immutable state arrays.

### 4. Life — Conway's Game of Life
- Entry point: [Life.java](src/main/java/rsp/app/gameoflife/Life.java)
- URL: <http://localhost:8082>
- Demonstrates: large grid rendering, click-to-toggle cells, control buttons (Start / Stop / Clear / Random), and the `onUpdated` / `onUnmounted` lifecycle hooks driving a `ScheduledExecutorService` to advance generations. Also shows serving CSS via `StaticResources`.

### 5. Counters — multiple components synced to the URL
- Entry point: [Counters.java](src/main/java/rsp/app/counters/Counters.java)
- URL: <http://localhost:8085/16/-1?c4=27>
- Demonstrates: a tree of components coordinated by [CountersMainComponent](src/main/java/rsp/app/counters/CountersMainComponent.java), extending `AddressBarSyncComponent` to map path elements and query parameters to context keys:
  - [ContextCounterComponent](src/main/java/rsp/app/counters/ContextCounterComponent.java) — `ContextStateComponent<Integer>` synced to a URL path element or query parameter.
  - [CachedCounterComponent](src/main/java/rsp/app/counters/CachedCounterComponent.java) — `StoredStateComponent<Integer>` whose state survives unmount via a shared `ConcurrentHashMap`.
  - [HideableCounterComponent](src/main/java/rsp/app/counters/HideableCounterComponent.java) — conditional rendering with `when(state, ...)` toggled by a checkbox.
  - [CountersView](src/main/java/rsp/app/counters/CountersView.java) — a single reusable view shared by all three counter types.
  - [CountersAppComponent](src/main/java/rsp/app/counters/CountersAppComponent.java) — top-level routing between the counters page and a 404 page.

### 6. CrudApp — full admin panel with AI agent
- Entry point: [CrudApp.java](src/main/java/rsp/app/posts/CrudApp.java)
- URL: <http://localhost:8085>
- Demonstrates the end-to-end `compositions` stack:
  - **Routing** — `Router` mapping `/posts`, `/posts/new`, `/posts/:id`, `/comments`, `/comments/:id` to contracts.
  - **Contracts + views** — list ([PostsListContract](src/main/java/rsp/app/posts/components/PostsListContract.java), [CommentsListContract](src/main/java/rsp/app/posts/components/CommentsListContract.java)) and edit/create forms ([PostEditContract](src/main/java/rsp/app/posts/components/PostEditContract.java), [PostCreateContract](src/main/java/rsp/app/posts/components/PostCreateContract.java), [CommentEditContract](src/main/java/rsp/app/posts/components/CommentEditContract.java), [CommentCreateContract](src/main/java/rsp/app/posts/components/CommentCreateContract.java)) bound through `DefaultListView` / `DefaultEditView`.
  - **Groups** — nested `Group("Admin") → Group("Posts") / Group("Comments")`; the tree drives the [ExplorerContract](src/main/java/rsp/app/posts/components/ExplorerContract.java) sidebar menu.
  - **Layout** — `DefaultLayout` with left sidebar (Explorer), right sidebar (Prompt), header, and a placement policy mapping forms inline and approvals to modals.
  - **Auth** — a separate `Composition` for `/auth/login` using `SimpleAuthProvider` + `SimpleLoginComponent`; `AuthComponent` redirects anonymous requests.
  - **AI agent** — [PromptContract](src/main/java/rsp/app/posts/components/PromptContract.java) talks to an `AgentService`, selectable via `-Dai.agent=regex|claude|ollama`. Backed by ABAC authorization (`AccessPolicy`, `Authorization`) and human-in-the-loop approvals via `ApprovalSpawner` + `DelegationApprovalContract`.
  - **Domain** — [PostService](src/main/java/rsp/app/posts/services/PostService.java), [CommentService](src/main/java/rsp/app/posts/services/CommentService.java), [RegexAgentService](src/main/java/rsp/app/posts/services/RegexAgentService.java), entities in [entities/](src/main/java/rsp/app/posts/entities/).

  Selecting the agent backend (run from `examples/` after the one-time `mvn install`):
  ```bash
  # default — deterministic regex stub, no external calls
  mvn exec:java -Dexec.mainClass=rsp.app.posts.CrudApp

  # Claude (requires ANTHROPIC_API_KEY)
  mvn exec:java -Dexec.mainClass=rsp.app.posts.CrudApp -Dai.agent=claude

  # local Ollama
  mvn exec:java -Dexec.mainClass=rsp.app.posts.CrudApp -Dai.agent=ollama
  ```

## Concept coverage

| Concept                                                  | Counter | PlainForm | JettyTodos | Life | Counters | CrudApp |
|----------------------------------------------------------|:-------:|:---------:|:----------:|:----:|:--------:|:-------:|
| `InitialStateComponent`                                  |    +    |           |     +      |      |          |         |
| Custom `Component<S>` subclass                           |         |     +     |            |  +   |    +     |    +    |
| Sealed-interface state                                   |         |     +     |            |      |    +     |         |
| `ElementRef` / form submit                               |         |           |     +      |      |          |    +    |
| HTTP method / query-param branching                      |         |     +     |            |      |          |         |
| Lifecycle hooks (`onUpdated` / `onUnmounted`)            |         |           |            |  +   |          |    +    |
| URL ↔ state sync (`AddressBarSyncComponent`)             |         |           |            |      |    +     |         |
| Persistent state across unmount (`StoredStateComponent`) |         |           |            |      |    +     |         |
| Conditional rendering (`when(...)`)                      |         |           |     +      |  +   |    +     |    +    |
| Static resources (`StaticResources`)                     |         |           |            |  +   |    +     |    +    |
| Routing (`Router` + contracts)                           |         |           |            |      |          |    +    |
| Layout + composition + groups                            |         |           |            |      |          |    +    |
| Auth composition                                         |         |           |            |      |          |    +    |
| AI agent + ABAC + HITL approval                          |         |           |            |      |          |    +    |
