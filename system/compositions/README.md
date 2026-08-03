# Compositions Module

The `compositions` module turns the [core](../core/README.md) component runtime
into an application framework for routed, schema-driven admin UIs. It provides
routing, layout, authorization, reusable list and form contracts, and a stable
component tree for sidebars, headers, prompts, and overlays.

## Architecture

Every interactive UI fragment is a component. A contract is the component that
owns its local cache, lifecycle work, subscriptions, effects, agent metadata,
and authorization. Its `ComponentView<S, I>` renders state and can only
dispatch typed intents. The view cannot mutate state or publish events.

```text
HTTP request
  -> AppComponent
  -> AutoAddressBarSyncComponent
  -> RoutingComponent
  -> AuthComponent
  -> SceneComponent
  -> DirectContractHost
  -> ContractNodeComponent<S, I>
  -> ComponentView<S, I>
```

`RoutingComponent` maps a path to a `Contract` class. `SceneComponent` keeps
the current descriptors, placement, return target, and effective URL. It does
not create a separate contract runtime or hold a live contract instance.
`DirectContractHost` supplies descriptor context and mounts the bound contract
component directly in the tree.

This gives one state owner per UI fragment:

- **Contract component**: local cache and behavior.
- **View**: rendering and typed intent dispatch.
- **Scene**: descriptors, placement, and URL-level navigation state.
- **Context**: immutable parent-provided inputs, not a replacement for local
  component state.

## Application Entry Point

An `App` is the request handler passed to a server adapter. It owns a `Config`,
the available `Composition`s, and app-wide `Services`.

```java
App app = new App(new Config(), List.of(postsComposition), services);
new WebServer(8080, app).start();
```

A `Composition` combines a `Router`, a `Layout`, and one or more `Group`s.
Compositions are considered in order; the first router that matches the path
wins.

```java
Router router = new Router()
        .route("/posts", PostsListContract.class)
        .route("/posts/new", PostCreateContract.class)
        .route("/posts/:id", PostEditContract.class);
```

Route patterns remain the source of truth for primary navigation, inline form
returns, and address-bar updates.

## Writing A Contract

Use `ContractNodeComponent<S, I>` for a custom contract, or extend one of the
schema-driven base components. A contract receives dependencies through its
constructor and receives the view it will render through the same constructor.

```java
public final class PostsListContract extends ListContractComponent<Post> {
    private static final QueryParam<Integer> PAGE = new QueryParam<>("p", Integer.class, 1);
    private static final QueryParam<String> SORT = new QueryParam<>("sort", String.class, "asc");

    private final PostService posts;

    public PostsListContract(PostService posts,
                             ComponentView<ListView.ListViewState, ListView.ListIntent> view) {
        super(view);
        this.posts = posts;
    }

    @Override protected QueryParam<Integer> pageQueryParam() { return PAGE; }
    @Override protected String sort(Lookup lookup) { return SORT.resolve(lookup); }
    @Override protected List<Post> items(int page, int pageSize, String sort) {
        return posts.findAll(page, pageSize, sort);
    }
    @Override public String title() { return "Posts"; }
}
```

The built-in bases cover common admin workflows:

- `ListContractComponent<T>` owns rows, schema, page, sort, selection, list
  actions, and query-driven cache refreshes.
- `FormContractComponent<T>` owns field values, validation, save, cancel, and
  form agent actions.
- `EditContractComponent<T>` adds path/show-data ID resolution and delete.

`DefaultListView` and `DefaultEditView` are `ComponentView` adapters, not
components and not state owners. They are reusable because they render the
state shape supplied by the contract and dispatch `ListIntent` or `EditIntent`.

## Views And Intents

Views receive an `IntentDispatcher<I>` and return a pure function of state.
State changes and side effects belong to the owning component.

```java
public record CounterState(int value) {}
public enum CounterIntent { INCREMENT }

ComponentView<CounterState, CounterIntent> view = intents -> state ->
        button(on("click", _ -> intents.dispatch(CounterIntent.INCREMENT)),
               text("Count: " + state.value()));

final class CounterComponent extends ContractNodeComponent<CounterState, CounterIntent> {
    @Override public ComponentStateSupplier<CounterState> initStateSupplier() {
        return (_, _) -> new CounterState(0);
    }

    @Override public ComponentView<CounterState, CounterIntent> componentView() {
        return view;
    }

    @Override protected void onIntent(CounterIntent intent, CounterState state,
                                      StateUpdater<CounterState> stateUpdater) {
        if (intent == CounterIntent.INCREMENT) {
            stateUpdater.setState(new CounterState(state.value() + 1));
        }
    }

    @Override public String title() { return "Counter"; }
}
```

This split is intentionally strict. A view never receives `StateUpdater`,
`Lookup`, or a command queue. Contracts use `StateUpdater<S>` in lifecycle and
intent handlers, and `lookup()` for context reads, events, and watches.

## Binding Contracts

`Group` binds a contract class to a supplier of a fresh component instance.
Groups also form the navigation and agent structure tree.

```java
Group main = new Group("Admin").description("Administration panel")
        .add(new Group("Posts").description("Blog posts")
                .bind(PostsListContract.class,
                        () -> new PostsListContract(postService, new DefaultListView()))
                .bind(PostCreateContract.class,
                        () -> new PostCreateContract(postService, new DefaultEditView()))
                .bind(PostEditContract.class,
                        () -> new PostEditContract(postService, new DefaultEditView())));
```

The supplier creates a new definition when a descriptor mounts. The mounted
`ComponentSegment` owns durable state for that instance. Constructor injection
keeps domain dependencies explicit and makes contract tests straightforward.

Use an unlabeled group for supporting contracts that should not become explorer
entries:

```java
Group support = new Group()
        .bind(ExplorerContract.class, () -> new ExplorerContract(main.structureTree()))
        .bind(HeaderContract.class, HeaderContract::new)
        .bind(PromptContract.class, () -> new PromptContract(/* dependencies */));
```

## Scene And Layout

`Scene` stores contract descriptors, not contract behavior. A descriptor names
the target class, instance ID, and optional show data. The component tree owns
the mounted contract, while scene handlers resolve semantic events such as
`SHOW`, `HIDE`, `SET_PRIMARY`, and `ACTION_SUCCESS` into descriptor and URL
transitions.

`Layout` determines where descriptors mount. `DefaultLayout` can keep header
and sidebar contracts mounted while the primary contract changes and can place
forms inline or in a modal layer.

```java
DefaultLayout layout = new DefaultLayout()
        .leftSidebar(ExplorerContract.class)
        .rightSidebar(PromptContract.class)
        .header(HeaderContract.class)
        .placement(FormContractComponent.class, Placement.INLINE.primary())
        .placement(DelegationApprovalContract.class, Placement.MODAL);
```

For query-only transitions such as pagination and sorting, a reusable list
contract updates its own cache and publishes the corresponding scene query
change. The scene updates its effective URL without recreating stable
companions. Browser back/forward produces fresh context; contracts that depend
on changing context watch the relevant keys.

## Context, Lookup, And Watches

`lookup()` is available after `onContractMounted(...)`. It follows the mounted
segment's current context, publishes typed events, and creates registrations
that are cleaned up automatically on unmount.

```java
@Override
protected void onContractMounted(HeaderViewState state,
                                 StateUpdater<HeaderViewState> stateUpdater) {
    watch(ContextKeys.PRIMARY_CATEGORY_KEY, category ->
            stateUpdater.applyStateTransformation(current ->
                    current.withCategory(category == null ? "" : category)));
}
```

Use context for inputs supplied by ancestors, such as URL values, route data,
auth data, and shared framework services. Copy a changing value into component
state only when it must affect the rendered cache, and keep it current with
`watch(...)`. `Contract.enrichContext(...)` remains available for the rare
case where a contract must supply context to descendants; it is not a
contract-to-view state channel.

## Services And Authorization

Pass domain services to contract constructors. Register shared framework
integration points in `Services` when they must be discovered through context.

```java
Services services = new Services()
        .service(AuthComponent.AuthProvider.class, authProvider);
```

Before mounting a descriptor, `DirectContractHost` calls
`Contract.isAuthorized(Lookup)`. The default implementation delegates to the
`Contract.AuthorizationStrategy` in context. Authorization is therefore a
contract-level decision before any view or state is exposed.

## Agent Integration

Contracts expose agent-facing capabilities through `contractMetadata()` and
`agentActions()`. The agent runtime receives the mounted primary `Contract`
through `PRIMARY_CONTRACT_MOUNTED`; it does not read a live contract from
`Scene`. `ContractProfile` recognizes the direct list, form, and edit component
bases and uses their declared action vocabulary to create typed event dispatch.

See [AI_AGENT_INTEGRATION.md](AI_AGENT_INTEGRATION.md) for the current agent
flow and [CrudApp](../../examples/src/main/java/rsp/app/posts/CrudApp.java) for
complete wiring.

## Testing

Test views as adapters by supplying an `IntentDispatcher`. Test contracts as
components by mounting their segment, dispatching typed intents, and checking
the resulting state or events. The compositions suite includes direct-host,
scene, routing, context-watch, and list cache refresh regressions.

Run it with:

```bash
mvn -pl system/compositions test
```
