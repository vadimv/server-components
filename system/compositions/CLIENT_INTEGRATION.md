# Client-side Integration Design

The framework is server-state-first. Java components own application state,
routing, authorization, and effects; the browser receives HTML and targeted DOM
or JavaScript commands. A client library is appropriate when the browser is the
right place for an imperative capability such as a map, chart, canvas, editor,
or drag gesture.

This document defines the integration boundary for those capabilities under the
direct contract component architecture.

## Ownership Rules

- A `ContractNodeComponent<S, I>` owns feature state, services, agent actions,
  and the semantic intent vocabulary.
- A `ComponentView<S, I>` renders server state and dispatches intents only.
- A child capability component may own the browser bridge lifecycle and its own
  small local cache, but it reports semantic interactions to its parent through
  typed intents or events.
- JavaScript holds ephemeral rendering objects only. It must not become the
  authority for business state, authorization, or navigation.

```text
Direct contract component
  -> intent-only contract view
  -> optional client capability component
  -> DOM container + client library instance
  -> semantic browser interaction
  -> typed component intent
  -> contract cache update or domain effect
```

There is no client-specific contract superclass and no registry that pairs a
contract type with a view. Bind the direct contract component in a `Group` and
pass its view or capability collaborators through its constructor like any
other dependency.

## Capability Component Shape

A capability component is an ordinary `Component<S, I>`. It owns the browser
container lifecycle and exposes a narrow typed intent vocabulary to its parent.

```java
record MapState(MapConfig config, boolean initialized) {}
sealed interface MapIntent permits MarkerSelected, ViewportChanged {}
record MarkerSelected(String markerId) implements MapIntent {}
record ViewportChanged(double latitude, double longitude, int zoom) implements MapIntent {}

final class MapCapabilityComponent extends Component<MapState, MapIntent> {
    @Override
    public ComponentStateSupplier<MapState> initStateSupplier() {
        return (_, context) -> new MapState(context.getRequired(MapConfig.class), false);
    }

    @Override
    public ComponentView<MapState, MapIntent> componentView() {
        return intents -> state -> div(attr("class", "map-container"));
    }

    @Override
    public void onAfterRendered(MapState state, Subscriber subscriber,
                                CommandsEnqueue commands, StateUpdater<MapState> stateUpdater) {
        if (!state.initialized()) {
            commands.offer(new RemoteCommand.EvalJs(0, initializeMap(state.config())));
            stateUpdater.setState(new MapState(state.config(), true));
        }
    }

    @Override
    protected void onIntent(MapIntent intent, MapState state,
                            StateUpdater<MapState> stateUpdater) {
        // Translate a client callback into the next server-owned state or event.
    }
}
```

The example is deliberately split: `ComponentView` describes the container and
intent boundary, while lifecycle callbacks receive `CommandsEnqueue` and
`StateUpdater`. A rendered view never receives either capability.

## Contract Integration

The feature contract remains the public behavior boundary. It can place a
capability component under its view and translate capability intents into
domain work.

```java
final class MapContract extends ContractNodeComponent<MapScreenState, MapScreenIntent> {
    private final MapService maps;

    MapContract(MapService maps) {
        this.maps = maps;
    }

    @Override
    public ComponentView<MapScreenState, MapScreenIntent> componentView() {
        return new MapScreenView();
    }

    @Override
    protected void onIntent(MapScreenIntent intent, MapScreenState state,
                            StateUpdater<MapScreenState> stateUpdater) {
        // Validate and apply semantic requests such as marker selection.
    }

    @Override public String title() { return "Map"; }
}

Group maps = new Group("Maps")
        .bind(MapContract.class, () -> new MapContract(mapService));
```

The contract can expose `contractMetadata()` and `agentActions()` just like a
list or form. The agent sees map-level domain actions, not arbitrary client
bridge commands.

## Browser Bridge Protocol

The bridge should be explicit and versioned. It needs only three operations:

1. **Initialize** a named client capability in a known container with JSON-safe
   configuration.
2. **Update** the existing capability when the server cache changes.
3. **Dispose** the capability when its component unmounts.

Browser callbacks should carry a capability ID, a known event name, and a
validated JSON payload. The server maps that callback to a typed intent or a
declared `EventKey`; it must reject unknown event names and malformed data.

```text
server state change -> EvalJs(updateConfig)
browser gesture     -> bridge callback
bridge callback     -> typed intent/event validation
contract            -> next server state or domain effect
unmount             -> EvalJs(dispose)
```

Avoid a generic `eval` callback channel that lets client code choose arbitrary
server events. Bridge event names belong to the capability component's defined
intent vocabulary.

## Resource Loading

Client libraries and style sheets should be loaded once per browser session,
with stable logical identifiers and explicit integrity/version metadata. A
capability component can request its resources during mount before it emits its
initialization command.

Loading policy should distinguish:

- **shared runtime assets**, cached for the application shell;
- **feature assets**, loaded when a feature contract first appears;
- **instance configuration**, always supplied from the current component state.

Do not put mutable configuration in a global JavaScript singleton. The current
component state is the source of each instance's configuration.

## CSS

Server-rendered CSS remains preferred for application layout and form/list UI.
Capability-specific CSS may be loaded with the library or injected as a scoped
resource owned by the capability component. Scope selectors to the capability
container so a chart or map library cannot accidentally restyle the rest of the
application.

The capability should clean up only resources it owns. Shared styles and
libraries normally remain cached for the browser session.

## Maps, Charts, Editors, And Canvas

These integrations follow the same rule even though their update strategies
differ:

| Capability | Browser responsibility | Server responsibility |
| --- | --- | --- |
| map | pan/zoom animation, tile rendering, hit testing | markers, permissions, selected entities, persisted viewport |
| chart | drawing and pointer interaction | series data, aggregation, selected range, business filters |
| rich editor | text editing mechanics and selection | document model, validation, save, collaboration policy |
| canvas/game surface | frame rendering and gesture sampling | authoritative state, rules, score, session policy |

High-frequency gestures should be sampled or coalesced client-side. Send a
semantic event when it affects server-owned state, not every raw pointer move.

## State Synchronization

Use one-way configuration updates from server state to the client instance.
When a callback changes server state, the component re-renders and emits a
targeted capability update from `onUpdated(...)` or a dedicated lifecycle
effect. The server should tolerate a capability being absent during initial
mount, replacement, or unmount.

For live streams, define a bounded update protocol: latest-value replacement,
batch windows, or a fixed-size ring buffer. Do not expose an unbounded client
event stream as application state.

## Testing

- Unit-test bridge payload parsing and typed intent translation without a
  browser.
- Component-test initialization, update, and dispose commands with a recording
  `CommandsEnqueue`.
- Browser-test the capability's visible behavior and cleanup using Playwright.
- Keep domain effects under contract tests; they should not depend on a
  JavaScript library being present.

This boundary keeps optional client libraries useful without weakening the
direct, server-owned contract model.
