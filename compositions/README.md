# Compositions Module

The `compositions` module turns the [core](../core/README.md) component runtime into
an admin-application framework. Where core gives you a single component tree
with state and events, `compositions` adds the structure that real applications
need:

- routing URLs to views,
- grouping related views into menus,
- placing them in sidebars, primary content, and modal overlays,
- separating *what a view does* (its contract) from *how it looks* (its view),
- authenticating users and gating protected routes,
- adapting forms and lists to schema-driven metadata.

It depends on [core](../core/README.md), [authorization](../authorization), and
[schema](../schema). The agent runtime, login providers, and example app live
in sibling modules.

## Layered Component Chain

A request flows through a fixed chain of components, each adding one concern.
Every component in the chain is an ordinary `Component<S>` from core, so the
runtime treats them uniformly.

```text
HTTP request
   │
   ▼
AuthComponent          authenticate, gate protected routes
   │
   ▼
UrlSyncComponent       keep state in sync with the address bar
   │
   ▼
RoutingComponent       match path → ViewContract class
   │
   ▼
SceneComponent         instantiate contracts, render Layer 0
   │
   ├── Layout          routed contract + companions (sidebars, header)
   │
   └── LayerComponent  Layer 1+ overlays, modals, panels
```

`SceneComponent` renders Layer 0 (the base layer). When something opens an
overlay or modal, `LayerComponent` stacks a `LayerLayout` on top and itself
recurses for further layers. The shape of the chain is fixed; what changes per
application is the `Composition`s plugged into `App`.

## Application Entry Point

An `App` is the request handler given to a server adapter (e.g.
`jetty-web-server`). It owns a `Config`, a list of `Composition`s, and a set of
shared `Services`.

```java
import rsp.compositions.application.App;
import rsp.compositions.application.Config;
import rsp.compositions.application.Services;
import rsp.jetty.WebServer;

App app = new App(new Config(), List.of(postsComposition), new Services());
new WebServer(8080, app).start();
```

`App` is a `Function<HttpRequest, Component<?>>` — every request gets a fresh
`AppComponent` rooted at the layered chain above.

## Compositions

A `Composition` is a feature unit: a `Router`, a `Layout`, and one or more
`Group`s that bind contract classes to factories and views.

```java
Composition posts = new Composition(router, layout, mainGroup, systemGroup);
```

`App` checks compositions in order; the first whose `Router` matches the URL
wins. Two natural uses:

- **One feature per composition** — e.g. a separate `Composition` for
  `/auth/login` so the login UI never depends on application services.
- **One composition for the whole app** — small apps register all routes in a
  single composition.

## Routes

`Router` maps URL patterns to `ViewContract` classes. Literal segments must be
registered before parameter routes.

```java
import rsp.compositions.routing.Router;

Router router = new Router()
        .route("/", PostsListContract.class)
        .route("/posts", PostsListContract.class)
        .route("/posts/new", PostCreateContract.class)
        .route("/posts/:id", PostEditContract.class);
```

The framework drives navigation from contract intent: when a contract publishes
`ACTION_SUCCESS`, the framework looks up the contract's route via
`Router.findRoutePattern(...)` and updates the URL. Contracts do not call
`setHref` directly.

## Contracts and Views

A *contract* owns the behaviour and data schema of a UI fragment. A *view*
renders it. Splitting the two lets the same form or list view serve many
contracts, and lets agents and tests drive a contract without rendering.

```java
import rsp.compositions.contract.EditViewContract;

public class PostEditContract extends EditViewContract<Post> {
    private final PostService service;

    public PostEditContract(Lookup lookup, PostService service) {
        super(lookup);
        this.service = service;
    }

    @Override public Post item()      { return service.find(resolveId()).orElse(null); }
    @Override public DataSchema schema() { return Post.SCHEMA; }
    @Override public boolean save(Map<String, Object> fields) { return service.update(resolveId(), Post.of(fields)); }
}
```

Built-in base contracts cover the common admin shapes:

- `ListViewContract<T>` — paged/sortable list with bulk actions.
- `CreateViewContract<T>` — empty form, save action.
- `EditViewContract<T>` — populated form, save and delete actions.
- `FormViewContract<T>` — base for form-shaped contracts.

Built-in views adapt to a `DataSchema` so the same view serves any entity type:

- `DefaultListView` — table with pagination, sort, selection, bulk delete.
- `DefaultEditView` — form generated from the schema's fields and types.

## Groups

`Group` binds contract classes to their factories and views, and gives the app
its menu structure. Groups nest: the label tree becomes the explorer
navigation.

```java
import rsp.compositions.composition.Group;

Group main = new Group("Admin").description("Administration panel")
        .add(new Group("Posts").description("Blog posts")
                .bind(PostsListContract.class,  ctx -> new PostsListContract(ctx, postService),  DefaultListView::new)
                .bind(PostCreateContract.class, ctx -> new PostCreateContract(ctx, postService), DefaultEditView::new)
                .bind(PostEditContract.class,   ctx -> new PostEditContract(ctx, postService),   DefaultEditView::new))
        .add(new Group("Comments").description("User comments")
                .bind(CommentsListContract.class, ctx -> new CommentsListContract(ctx, commentService), DefaultListView::new));
```

Each `bind(...)` is a 1-1-1: contract class, contract factory taking a
`Lookup`, and a view component supplier. Unlabeled groups (`new Group()`) are
useful for system contracts (explorer, prompt, header) that exist to support
the page but should not appear in the navigation tree.

`Group.structureTree()` returns a lightweight `StructureNode` (labels +
descriptions + contract classes — no factories) consumed by the navigation
sidebar and by agents that need to reason about the application's shape.

## Scene

A `Scene` is the immutable snapshot of *what is currently mounted*: the
routed contract, the eagerly instantiated companions the layout asked for, and
the lazy factories that can be activated on demand.

Lifecycle is derived, not declared:

- **Eager** — routed contracts (matched by the router) and contracts named by
  `Layout.requiredContracts()` are instantiated up front by `SceneBuilder`.
- **Lazy** — everything else is stored as a factory and instantiated on a
  `SHOW` event.

`Scene` carries enough state for back/forward navigation and inline form
returns (`InlineReturnTarget`, `effectiveUrl`), so a contract switching
between primary and inline placements does not have to track URL state itself.

## Layout

`Layout` is a strategy that resolves a `Scene` into a `Definition`. It also
declares which non-routed contracts it needs eagerly instantiated, and where
each contract appears.

```java
import rsp.compositions.layout.DefaultLayout;
import rsp.compositions.layout.GroupPlacementPolicy;
import rsp.compositions.layout.Placement;

DefaultLayout layout = new DefaultLayout()
        .leftSidebar(ExplorerContract.class)
        .rightSidebar(PromptContract.class)
        .header(HeaderContract.class)
        .groupPlacementPolicy(GroupPlacementPolicy.FIRST_IN_GROUP_INLINE_OTHERS_MODAL)
        .placement(FormViewContract.class,         Placement.INLINE.primary())
        .placement(DelegationApprovalContract.class, Placement.MODAL);
```

The base layer is rendered by the active `Layout`. Higher layers (overlays,
modals) are rendered by `LayerLayout` implementations — `ModalLayerLayout` is
provided out of the box; custom layer layouts can implement side panels,
toasts, or whatever the application needs.

`Placement` is a hint (`INLINE.primary()` or `MODAL`). The
`GroupPlacementPolicy` resolves what to do for contracts the layout has not
been told about explicitly — e.g. *"first contract in the group inlines,
everything else opens modally"*.

## Events

Contracts and views communicate through framework events. The important ones:

- `SHOW(class, data)` — open a contract on demand. The scene decides whether
  to replace inline content or stack a layer (`SHOW_LAYER`).
- `HIDE(class)` — close a specific contract, calling its `onDestroy`.
- `SET_PRIMARY(class)` — make a contract the routed primary.
- `ACTION_SUCCESS(result)` — a contract finished a save/delete; the framework
  navigates back to the list route. The framework imposes no auto-close
  policy — contracts that want to close themselves publish `HIDE`.
- `STATE_UPDATED(*)` — context parameters changed (e.g. page or sort); the URL
  is updated.

Contracts declare what an external caller (typically an agent) can dispatch
via `agentActions()` returning a list of `ContractAction`. Each action binds a
human-readable name + payload schema + `DispatchEffect` to an internal
`EventKey`.

## Auth

`AuthComponent` sits at the top of the chain. It reads an `AuthProvider` from
context, authenticates the request, enriches the context with
`AUTH_USER` / `AUTH_AUTHENTICATED` / `AUTH_ROLES`, and gates protected routes.

The login UI is just another composition with its own router and group:

```java
SimpleAuthProvider authProvider = new SimpleAuthProvider();

Composition auth = new Composition(
        new Router().route("/auth/login", LoginContract.class),
        new DefaultLayout(),
        new Group().bind(LoginContract.class,
                         LoginContract::new,
                         () -> new SimpleLoginComponent(authProvider)));

Services services = new Services().service(AuthComponent.AuthProvider.class, authProvider);

App app = new App(new Config(), List.of(auth, postsComposition), services);
```

`PublicAccessStrategy` and `AuthenticatedOnlyStrategy` are ready-made
`ViewContract.AuthorizationStrategy` implementations; richer per-contract
authorization lives in the `authorization` module.

## Context and Lookup

`Lookup` is the unified API a contract uses to read context, publish events,
and subscribe to them. It is constructed once per contract instance from the
current `ComponentContext`, the framework's command queue, and the active
subscriber.

Component-level context flows downward through `subComponentsContext()`
overrides in the chain components above. The framework-defined keys live in
[`ContextKeys`](src/main/java/rsp/compositions/contract/ContextKeys.java).

### Watching Context

`lookup.get(KEY)` returns a snapshot — the value at the moment the contract
was constructed. When the watched value can change while the contract is
mounted (e.g. a sibling contract publishes a new selected category through
context), a contract calls `watch(...)` to be notified.

```java
public class HeaderContract extends ViewContract {

    private String currentCategory;

    public HeaderContract(Lookup lookup) {
        super(lookup);
        this.currentCategory = lookup.get(ContextKeys.PRIMARY_CATEGORY_KEY);

        watch(ContextKeys.PRIMARY_CATEGORY_KEY, (previous, next) ->
                this.currentCategory = next);
    }
}
```

The handler fires when the contract's component segment is reused with a new
`ComponentContext` and the value at that key changes by `Objects.equals`.
Registration cleanup is automatic: `ViewContract.watch(...)` adds the
`Registration` to the contract's handler set and unsubscribes it in
`onDestroy()`.

`watch(...)` is what makes contract reuse useful. Reading context once at
construction is sufficient when a contract is recreated every time its inputs
change — but recreating it on every parent re-render is wasteful for
companions like a header, sidebar, or prompt that should keep their own
state across navigations. Contracts and views that opt into reuse
(`isReusable() == true`) keep their segment across re-renders and use
`watch(...)` to stay in sync with the new context. The two-overload shape
(`BiConsumer<T,T>` for old+new, `Consumer<T>` for new only) mirrors how event
subscriptions work, so the same registration discipline applies.

## Services

There are two ways a contract receives its dependencies, and the choice
depends on *who instantiates it*.

### Constructor injection via the `bind(...)` factory

For contracts your application instantiates itself, pass the dependency as a
constructor argument. The `bind(...)` factory is a `Function<Lookup, ViewContract>`,
so closing over a local service variable is the natural pattern:

```java
PostService postService = new PostService();

Group posts = new Group("Posts")
        .bind(PostsListContract.class,
              lookup -> new PostsListContract(lookup, postService),
              DefaultListView::new);
```

```java
public class PostsListContract extends ListViewContract<Post> {
    private final PostService postService;

    public PostsListContract(Lookup lookup, PostService postService) {
        super(lookup);
        this.postService = postService;
    }
}
```

This keeps dependencies explicit at the call site, makes the contract trivial
to construct in tests, and avoids any registry indirection. Use it for
ordinary application services — repositories, domain services, formatters.

### The `Services` registry

`Services` is a typed singleton registry — one instance per `Class<?>` key —
that the framework merges into the root `ComponentContext`. Use it when the
service must be reachable from code you do **not** construct directly:

- A framework component reads it from context (e.g. `AuthComponent` looks up
  the `AuthProvider`).
- A contract instantiated lazily by the framework from a `SHOW` event factory
  needs to reach a session-scoped collaborator.
- Cross-cutting infrastructure that many contracts pull on demand and you
  don't want to thread through every `bind(...)` lambda.

```java
SimpleAuthProvider authProvider = new SimpleAuthProvider();

Services services = new Services()
        .service(AuthComponent.AuthProvider.class, authProvider);

App app = new App(new Config(), List.of(authComposition, postsComposition), services);
```

`Services` has two scopes: registered on `App` it is shared by every
composition; registered on a `Composition` it shadows app-level entries with
the same key for contracts inside that composition.

Reach a registered service through `lookup.get(SomeService.class)`. The two
mechanisms compose freely — most apps inject domain services through
constructors and reserve the registry for framework integration points and
genuinely cross-cutting collaborators.

### Lifecycle Hooks

A service that needs to start work when a session begins, or release
resources when it ends, implements `ServicesLifecycleHandler`:

```java
public class PromptService implements ServicesLifecycleHandler {
    @Override public void onStart(Lookup lookup) { startTicking(); }
    @Override public void onStop()               { stopTicking(); }
}
```

Hooks fire at the matching scope boundary — app services receive `onStart`
when the session is created and `onStop` when it ends; composition services
receive them when their composition's scene activates and deactivates. Only
services registered through `Services` participate in lifecycle hooks;
services injected by closure capture in `bind(...)` factories do not.

## Running an Example

The [examples](../examples/README.md) module ships `CrudApp`, which wires
everything described here into a full posts/comments admin panel with an AI
prompt sidebar.
