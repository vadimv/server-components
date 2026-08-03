# Composable UI Design

This document describes the current composable UI model. A contract is a
state-owning component mounted directly in the tree. Its view is an adapter
that renders immutable state and dispatches typed intents.

## Design Goals

- One owner for each user-visible cache and lifecycle resource.
- Reusable schema-driven list and form views without giving views mutation
  capabilities.
- Typed intent and event boundaries that are easy to test and expose to agents.
- Stable companions such as headers, explorers, and prompts across primary
  route changes.
- Scene-managed placement and URL transitions without a second contract
  runtime.

## Component Shape

`ContractNodeComponent<S, I>` is the base for a direct contract component.
`S` is local cache state; `I` is the vocabulary the view may dispatch.

```java
public final class SearchContract
        extends ContractNodeComponent<SearchState, SearchIntent> {

    @Override
    public ComponentStateSupplier<SearchState> initStateSupplier() {
        return (_, _) -> new SearchState("", List.of());
    }

    @Override
    public ComponentView<SearchState, SearchIntent> componentView() {
        return new SearchView();
    }

    @Override
    protected void onIntent(SearchIntent intent, SearchState state,
                            StateUpdater<SearchState> stateUpdater) {
        if (intent instanceof SearchRequested request) {
            stateUpdater.setState(new SearchState(request.query(), search(request.query())));
        }
    }

    @Override public String title() { return "Search"; }
}
```

The corresponding view has no `Lookup` or `StateUpdater`:

```java
public final class SearchView implements ComponentView<SearchState, SearchIntent> {
    @Override
    public View<SearchState> use(IntentDispatcher<SearchIntent> intents) {
        return state -> form(on("submit", true,
                _ -> intents.dispatch(new SearchRequested(state.query()))));
    }
}
```

The component can update state in `onIntent`, `onContractMounted`, context
watches, and asynchronous callbacks. The view cannot bypass those rules.

## Schema-Driven CRUD

The reusable bases are components rather than a separate contract/view layer.

| Base | Owns | View intent type |
| --- | --- | --- |
| `ListContractComponent<T>` | visible rows, schema, page, sort, selection, list events | `ListView.ListIntent` |
| `FormContractComponent<T>` | form values, validation, save/cancel lifecycle | `EditView.EditIntent` |
| `EditContractComponent<T>` | form state plus ID resolution and deletion | `EditView.EditIntent` |

`DefaultListView` and `DefaultEditView` implement the corresponding
`ComponentView` interfaces. They can be supplied to many contracts through
their constructors:

```java
.bind(PostsListContract.class,
      () -> new PostsListContract(postService, new DefaultListView()))
.bind(PostEditContract.class,
      () -> new PostEditContract(postService, new DefaultEditView()))
```

The view produces intents such as `PageRequested`, `SortRequested`,
`FormValuesCollected`, and `DeleteConfirmed`. The base component validates,
updates its local cache, and publishes the semantic scene or domain event.

## List State And URL State

A list's rendered page, sort, selection, rows, and schema belong to its
`ListViewState`. Page and sort intents reload that cache before the next render.
The contract also publishes a query update so the address bar reflects the
same state.

For query-only transitions, `Scene` keeps an effective URL while the layout
and direct contract components remain mounted. A reusable list watches the
`p` and `sort` context keys, so browser back/forward reloads the cache from the
new context. This preserves local ownership without treating context as the
source of every render value.

## Forms And Editing

`FormContractComponent` derives initial fields from the entity and `DataSchema`.
It owns validation errors and dirty state. On a form intent it either updates
the local draft or invokes `save(...)`; success publishes `ACTION_SUCCESS`.

`EditContractComponent` resolves the current ID from show data or a route path
parameter, supplies the existing entity, and adds delete support. A create
contract extends `FormContractComponent` and supplies create-mode behavior.

```java
public final class PostEditContract extends EditContractComponent<Post> {
    public PostEditContract(PostService posts,
                            ComponentView<EditViewState, EditIntent> view) {
        super(view);
        this.posts = posts;
    }

    @Override protected String resolveIdFromPath(Lookup lookup) { return POST_ID.resolve(lookup); }
    @Override protected Post item(String id) { return posts.find(id).orElse(null); }
    @Override public boolean save(Map<String, Object> values) { /* persist values */ }
    @Override protected boolean delete(String id) { return posts.delete(id); }
}
```

The scene interprets `ACTION_SUCCESS`: it refreshes the routed contract or
returns from an inline form to its captured list route and query state.

## Placement And Layers

`Scene` stores descriptors for the routed primary contract and optional layer
contracts. A descriptor holds class, instance identity, and show data. It does
not hold a view or a live contract object.

`DirectContractHost` mounts the descriptor's bound component and gives it
descriptor context. `Layout` and `Placement` determine whether a `SHOW` event
replaces the primary fragment inline or opens a layer.

```java
DefaultLayout layout = new DefaultLayout()
        .leftSidebar(ExplorerContract.class)
        .header(HeaderContract.class)
        .placement(FormContractComponent.class, Placement.INLINE.primary())
        .placement(DelegationApprovalContract.class, Placement.MODAL);
```

Stable companions are ordinary direct contracts. Their cache survives primary
navigation because the layout keeps their component segment mounted.

## Context And Descendants

Context is inherited input. A contract reads it after mount through `lookup()`
and watches values that can change while the component stays mounted.

```java
watch(ContextKeys.PRIMARY_CATEGORY_KEY, category ->
        stateUpdater.applyStateTransformation(state -> state.withCategory(category)));
```

`Contract.enrichContext(...)` may add data for descendant components. It should
not be used to hand state to the contract's own view: that state belongs in the
component state snapshot supplied to `ComponentView`.

## Events And Agent Actions

Views dispatch local intents. Contracts publish typed framework events for
cross-component work:

| Event | Owner |
| --- | --- |
| `SHOW` / `HIDE` / `SET_PRIMARY` | scene placement and navigation |
| `ACTION_SUCCESS` | successful form/delete workflow completion |
| `SCENE_QUERY_UPDATED` | query state under the scene's effective URL |
| contract-specific event keys | agent actions and cross-contract behavior |

Contracts expose agent capabilities through `ContractMetadata` and
`ContractAction`. The agent dispatcher can only publish an event declared by
the currently mounted contract, after payload parsing and authorization.

## Testing Boundaries

- Test a view with a recording `IntentDispatcher` and assert the emitted intent.
- Test a contract by mounting its component segment, dispatching an intent, and
  asserting its next local state or published event.
- Test scene handlers for descriptor, placement, effective URL, and return
  transitions.
- Test context-dependent reusable contracts with a parent context change and a
  watch regression.

This arrangement keeps rendering adapters simple while making behavior,
state, and side effects explicit in components.
