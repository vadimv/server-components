# Compositions Module

The `compositions` module turns the [core](core.md) component runtime
into an application framework for routed, schema-driven admin UIs. It provides
routing, layout, authorization, reusable list and form blocks, and a stable
component tree for sidebars, headers, prompts, and overlays.

## Architecture

Every interactive UI fragment is a component. A block is the component that
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
  -> DirectBlockHost
  -> Block<S, I>
  -> ComponentView<S, I>
```

`RoutingComponent` maps a path to a block key, then resolves that key through
the composition's groups to a `BlockTarget` containing both the binding key and
its `Block<?, ?>` class. `SceneComponent` keeps the current descriptors,
placement, return target, and effective URL. It does not create a separate
block runtime or hold a live block instance.
`DirectBlockHost` supplies descriptor context and mounts the bound block
component directly in the tree.

This gives one state owner per UI fragment:

- **Group**: assembly, navigation hierarchy, and keyed factories for blocks.
- **Block**: the state-owning component and behavior boundary.
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
Object postsKey = new Object();

Router router = new Router()
        .route("/posts", postsKey)
        .route("/posts/new", PostCreateBlock.class)
        .route("/posts/:id", PostEditBlock.class);
```

Every route target is a non-null object key. The class overload remains the
compact form: `route(path, PostsListBlock.class)` uses `PostsListBlock.class`
as both key and type identity. A custom key separates binding identity from
Java type, so the same block class can be configured more than once. Reuse the
same key (or an equal key) in the router, group, layout, and block events. A
plain `new Object()` is an identity token; enums, strings, or value objects can
provide more readable diagnostics when appropriate.

`Router.match(...)` exposes the selected identity through
`RouteMatch.blockKey()`. Its `blockClass()` accessor is compatibility-only and
throws for a custom-keyed route; resolve the key through `Group.target(key)`
when both the key and configured class are needed.

Route patterns remain the source of truth for primary navigation, inline form
returns, and address-bar updates.

The resulting composition DSL is `Group -> Block -> View`: a group answers
which blocks exist and how they are organized; each block owns a UI fragment;
its view is a pure adapter from state and intents to markup. `BlockRuntime` is
only the narrow capability interface used by authorization and agent tooling.

## Writing A Block

Use `Block<S, I>` for a custom block, or extend one of the
schema-driven base components. A block receives dependencies through its
constructor and receives the view it will render through the same constructor.

```java
public final class PostsListBlock extends ListBlock<Post> {
    private static final QueryParam<Integer> PAGE = new QueryParam<>("p", Integer.class, 1);
    private static final DataSchema SCHEMA = DataSchema.builder()
            .field("id", FieldType.ID)
            .field("title", FieldType.STRING)
            .field("content", FieldType.TEXT)
            .column("id").sortable().width("6rem")
            .column("title").sortable().filterable().width("30%")
            .column("content").filterable().width("auto")
            .build()
            .withSelectable(true);

    private final PostService posts;

    public PostsListBlock(PostService posts,
                             ComponentView<ListView.ListViewState, ListView.ListIntent> view) {
        super(view);
        this.posts = posts;
    }

    @Override protected QueryParam<Integer> pageQueryParam() { return PAGE; }
    @Override protected DataSchema listSchema() { return SCHEMA; }
    @Override protected SortSpec defaultSort() {
        return new SortSpec("title", SortDirection.ASC);
    }
    @Override protected ListPage<Post> items(ListQuery query) { return posts.findAll(query); }
    @Override protected DeleteResult bulkDelete(Set<String> ids) { return posts.deleteAll(ids); }
    @Override public String title() { return "Posts"; }
}
```

The built-in bases cover common admin workflows:

- `ListBlock<T>` owns stable schema, validated query state, exact result totals,
  selection, CRUD capabilities, feedback messages, and URL-synchronized cache
  refreshes. Search, filters, sort, page, and page size travel together in a
  `ListQuery`; the loader returns a `ListPage<T>`.
- `FormBlock<T>` owns field values, validation, save, cancel, and
  form agent actions.
- `EditBlock<T>` adds path/show-data ID resolution and delete.

`DefaultListView` and `DefaultEditView` are `ComponentView` adapters, not
components and not state owners. They are reusable because they render the
state shape supplied by the block and dispatch `ListIntent` or `EditIntent`.

## Views And Intents

Views receive an `IntentDispatcher<I>` and return a pure function of state.
State changes and side effects belong to the owning component.

```java
public record CounterState(int value) {}
public enum CounterIntent { INCREMENT }

ComponentView<CounterState, CounterIntent> view = intents -> state ->
        button(on("click", _ -> intents.dispatch(CounterIntent.INCREMENT)),
               text("Count: " + state.value()));

final class CounterComponent extends Block<CounterState, CounterIntent> {
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
`Lookup`, or a command queue. Blocks use `StateUpdater<S>` in lifecycle and
intent handlers, and `lookup()` for context reads, events, and watches.

## Binding Blocks

`Group` binds a key and block class to a supplier of a fresh component
instance. The class is retained as the typed second parameter even when a
custom key is used. Groups also form the navigation and agent structure tree.

```java
Object postsKey = new Object();

Group main = new Group("Admin").description("Administration panel")
        .add(new Group("Posts").description("Blog posts")
                .bind(postsKey, PostsListBlock.class,
                        () -> new PostsListBlock(postService, new DefaultListView()))
                .bind(PostCreateBlock.class,
                        () -> new PostCreateBlock(postService, new DefaultEditView()))
                .bind(PostEditBlock.class,
                        () -> new PostEditBlock(postService, new DefaultEditView())));
```

The two-parameter form is shorthand for the three-parameter form with
`blockKey == blockClass`:

```java
group.bind(PostCreateBlock.class, factory);
// Equivalent to:
group.bind(PostCreateBlock.class, PostCreateBlock.class, factory);
```

Use `blockTargets()` when binding identity matters. It returns each
`BlockTarget(key, blockClass)` and therefore preserves multiple bindings of the
same class. `blockClasses()` remains a compatibility/type-oriented view and
deduplicates repeated classes.

The supplier creates a new definition when a descriptor mounts. The mounted
`ComponentSegment` owns durable state for that instance. Constructor injection
keeps domain dependencies explicit and makes block tests straightforward.

Use an unlabeled group for supporting blocks that should not become explorer
entries:

```java
Group support = new Group()
        .bind(ExplorerBlock.class, () -> new ExplorerBlock(main.structureTree()))
        .bind(HeaderBlock.class, HeaderBlock::new)
        .bind(PromptBlock.class, () -> new PromptBlock(/* dependencies */));
```

### Composition Validation And Sealing

Constructing a `Composition` validates the complete configuration before any
request is served:

- Block keys must be unique across all nested and merged groups. Key equality
  follows normal `Map` semantics (`equals` and `hashCode`).
- Every route key must have a group binding.
- Every companion key required by the layout must have a group binding.

After successful validation, the router and every group in the composition are
sealed. Later calls to `route`, `bind`, `add`, or `description` fail instead of
silently changing a running composition. If construction fails validation, the
inputs remain mutable so the configuration can be corrected.

## Scene And Layout

`Scene` stores block descriptors, not block behavior. A descriptor names the
binding key, target class, instance ID, and optional show data. The key is the
runtime identity; the class supplies Java type information and class-based
placement rules. The component tree owns the mounted block, while scene
handlers resolve semantic events such as `SHOW`, `HIDE`, `SET_PRIMARY`, and
`ACTION_SUCCESS` by key into descriptor and URL transitions. A mounted block
can read its configured identity with `blockKey()`.

`Layout` determines where descriptors mount. `DefaultLayout` can keep header
and sidebar blocks mounted while the primary block changes and can place
forms inline or in a modal layer.

```java
Object explorerKey = new Object();

Group support = new Group()
        .bind(explorerKey, ExplorerBlock.class,
                () -> new ExplorerBlock(main.structureTree()))
        .bind(PromptBlock.class, () -> new PromptBlock(/* dependencies */))
        .bind(HeaderBlock.class, HeaderBlock::new);

DefaultLayout layout = new DefaultLayout()
        .leftSidebar(explorerKey)
        .rightSidebar(PromptBlock.class)
        .header(HeaderBlock.class)
        .placement(FormBlock.class, Placement.INLINE.primary())
        .placement(DelegationApprovalBlock.class, Placement.MODAL);
```

The fixed `leftSidebar`, `rightSidebar`, and `header` slots accept either a
custom key or a block class. Their keys are reported through
`requiredBlockKeys()` and validated when the composition is constructed.
Placement rules remain type-based:
`placement(Class<? extends BlockRuntime>, Placement)` applies to every keyed
binding assignable to that type. Custom layouts can use
`resolvePlacement(BlockTarget, Scene)` when both key and class are relevant.

For query-only transitions such as search, filtering, pagination, page size,
and sorting, a reusable list block updates its own cache and publishes all
related query changes as one scene update. The scene creates one browser
history entry and updates its effective URL without recreating stable
companions. Browser back/forward produces fresh context; blocks watch all grid
query keys and reload from that context. Edit links carry the full grid query
in `fromQuery`, so save or cancel returns to the same result set.

The standard grid URL contract is `p`, `size`, `sort`, `dir`, `q`, and
`filter.<field>`. Invalid pages and page sizes are bounded, sort fields are
restricted to sortable schema columns, filter fields are restricted to
filterable columns, and an out-of-range page is clamped after loading the exact
total. See the [data-grid guide](../guides/data-grid.md).

## Context, Lookup, And Watches

`lookup()` is available after `onBlockMounted(...)`. It follows the mounted
segment's current context, publishes typed events, and creates registrations
that are cleaned up automatically on unmount.

```java
@Override
protected void onBlockMounted(HeaderViewState state,
                                 StateUpdater<HeaderViewState> stateUpdater) {
    watch(ContextKeys.PRIMARY_CATEGORY_KEY, category ->
            stateUpdater.applyStateTransformation(current ->
                    current.withCategory(category == null ? "" : category)));
}
```

Use context for inputs supplied by ancestors, such as URL values, route data,
auth data, and shared framework services. Copy a changing value into component
state only when it must affect the rendered cache, and keep it current with
`watch(...)`. `BlockRuntime.enrichContext(...)` remains available for the rare
case where a block must supply context to descendants; it is not a
block-to-view state channel.

## Services And Authorization

Pass domain services to block constructors. Register shared framework
integration points in `Services` when they must be discovered through context.

```java
Services services = new Services()
        .service(AuthComponent.AuthProvider.class, authProvider);
```

Before mounting a descriptor, `DirectBlockHost` calls
`BlockRuntime.isAuthorized(Lookup)`. The default implementation delegates to the
`BlockRuntime.AuthorizationStrategy` in context. Authorization is therefore a
block-level decision before any view or state is exposed.

## Agent Integration

Blocks expose agent-facing capabilities through `blockMetadata()` and
`agentActions()`. The agent runtime receives the mounted primary `BlockRuntime`
through `PRIMARY_BLOCK_MOUNTED`; it does not read a live block from
`Scene`. Navigation preserves the selected `BlockTarget`, so two explorer
entries backed by the same class still navigate to their distinct keys.
`BlockProfile` recognizes the direct list, form, and edit component bases and
uses their declared action vocabulary to create typed event dispatch.

See [AI agent integration](agent-integration.md) for the current
agent flow and [CrudApp](../../examples/src/main/java/rsp/app/posts/CrudApp.java)
for complete wiring.

## Testing

Test views as adapters by supplying an `IntentDispatcher`. Test blocks as
components by mounting their segment, dispatching typed intents, and checking
the resulting state or events. For custom keys, cover routing and events by key
rather than identifying a target only by class. The compositions suite includes
duplicate/unbound-key validation, same-class/different-key scenes, direct-host,
scene, routing, context-watch, and list cache refresh regressions.

Run it with:

```bash
mvn -pl system/compositions test
```
