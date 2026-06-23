# Client-side Integration Design

This document describes how to integrate browser-side capabilities with the server-components framework while preserving the core product promise: application code should remain Java-first.

The goal is not to turn applications into JavaScript applications. The goal is to keep the always-loaded `js-client` small, then load reusable JavaScript capability modules only when a Java component asks for browser-native behavior such as drag-and-drop, pan/zoom, graph rendering, canvas drawing, FLIP animations, maps, charts, or editor widgets.

For the target class of Java business and realtime operational UIs, this project should be sufficient as the only UI dependency for nearly all common production needs.
When a block is missing, teams should be able to build it themselves, often with AI assistance, by composing framework primitives rather than introducing a new frontend stack.
Third-party JavaScript libraries remain escape hatches for specialized renderers, algorithms, or domains whose complexity is not worth owning in the framework.

Target first-party functionality scope:

- layout, panels, modals, drawers, tabs
- forms, validation, schema-driven editors
- tables, filtering, pagination, selection
- navigation, routing, auth shells
- theming and design tokens
- notifications, toasts, command/search palettes
- drag/drop, resize, sortable, pan/zoom
- standard charts and live graphs
- node diagrams and workflows to a solid baseline
- animation primitives
- accessibility defaults

This is a target design. Some primitives already exist (`evalJs`, element references, DOM events, static resources); others are called out below as framework gaps that should be implemented before building serious bridge APIs.


## Current Primitives

| API                                       | Purpose                | Returns                           |
|-------------------------------------------|------------------------|-----------------------------------|
| `ctx.evalJs(code)`                        | Execute JS on client   | `CompletableFuture<JsonDataType>` |
| `ctx.evalJs(code, callback)`              | Execute with callback  | `void`                            |
| `ctx.propertiesByRef(ref).get(prop)`      | Read element property  | `CompletableFuture<JsonDataType>` |
| `ctx.propertiesByRef(ref).set(prop, val)` | Write element property | `void`                            |
| `ElementRef` + `ref(ref)`                 | Reference DOM element  | Server-side handle                |
| `onAfterRendered()`                       | Lifecycle hook         | Called after DOM exists           |

Current gaps to close:

- `evalJs(...)` supports server to browser execution, but richer capabilities need a reusable resource loader (`ensureScript`, `ensureStylesheet`, cache modes, lifecycle cleanup).
- The client exposes a custom callback hook, but the server protocol must route custom browser callbacks into server-side semantic events.
- Built-in DOM event payloads are intentionally sparse. Pointer-heavy capabilities should emit semantic events (`dragCommitted`, `nodeMoved`, `edgeCreated`) rather than raw `pointermove` floods.
- Application code should not write JavaScript strings directly. JavaScript should be hidden behind Java components, contracts, DSL helpers, and capability descriptors.

Recommended implementation order:

1. Add server decoding/routing for semantic custom callbacks.
2. Add `RSP.resources` and `RSP.capabilities` to the client kernel.
3. Add Java resource/capability wrappers so application code does not call `evalJs` directly.
4. Add the common interaction runtime for drag/drop, pan/zoom, selection, and FLIP.
5. Build specific bridges such as maps, live graphs, diagrams, kanban, and editors on top.

---

## Refined Architecture

The integration stack should have four layers:

```text
Application Java
  Pure Java contracts, services, components, state records, DSL calls

Java capability APIs
  DiagramView, LiveGraphView, SortableList, DragDrop, Animation, MapView

Client capability runtime
  Resource loading, mount/update/unmount, callbacks, descriptors, interaction registry

On-demand JavaScript modules
  Pointer capture, hit testing, transforms, FLIP, chart/canvas/SVG/library rendering
```

The always-loaded `js-client` remains the kernel:

- WebSocket connection and protocol dispatch
- DOM patch application
- basic DOM event forwarding
- `evalJs(...)`
- element lookup
- resource loading and callback transport

Everything richer should be loaded on demand.

## Pure Java Application Contract

Application-level code should talk in Java concepts and should not see JavaScript:

```java
nodeGraph()
    .nodes(state.nodes())
    .edges(state.edges())
    .panZoom(true)
    .draggableNodes(true)
    .onNodeMoved(DiagramEvents.NODE_MOVED)
    .onEdgeCreated(DiagramEvents.EDGE_CREATED);
```

For generic gestures:

```java
div(
    interaction()
        .draggable()
        .dropGroup("kanban-card")
        .commitOnEnd(KanbanEvents.CARD_DROPPED),
    text(card.title())
)
```

Under the hood, those Java helpers render normal DOM/SVG/container elements, attach serialized descriptors, ensure the required JavaScript module is loaded, mount the behavior after render, and translate browser gestures into server-side semantic events.

## Common JavaScript Capability Runtime

Specific bridges should not each invent pointer handling, callback transport, cleanup, or animation scheduling. A common runtime should provide:

- resource loader: `RSP.resources.ensureScript(...)`, `ensureStylesheet(...)`, inline style injection, cache modes
- capability registry: mount/update/unmount by component id
- callback transport: `RSP.callbacks.emit(name, payload)`
- descriptor scanning: find `data-rsp-capability` nodes after server patches
- pointer utilities: pointer capture, drag tracking, hit testing, throttling, debouncing
- interaction primitives: drag, drop, resize, sortable, selection rectangle, pan/zoom
- animation primitives: FLIP measurement, enter/leave/move animations, reduced-motion handling
- cleanup: remove listeners, observers, animation frames, and bridge instances when components unmount

Specific bridges become thin consumers:

| Bridge           | Reuses                               | Adds                                        |
|------------------|--------------------------------------|---------------------------------------------|
| Diagram          | drag, pan/zoom, callbacks, animation | ports, edges, node positions, edge previews |
| Sortable list    | drag/drop, hit testing               | item ordering and placeholders              |
| Kanban           | drag/drop, sortable                  | columns, lane constraints, card moves       |
| Live graph/chart | resource loader, animation loop      | axes, data buffers, rendering strategy      |
| Map              | loader, callbacks, lifecycle         | Leaflet/OpenLayers integration              |

## On-Demand Library Loading with Caching

### The Concept

Instead of including all libraries in `<head>`, load them on demand when a component first needs them. Application code should not call this directly; capability components do it internally.

```java
// Low-level primitive used by a Java capability component.
ctx.evalJs("""
    RSP.resources.ensureScript('/res/rsp-diagram.js')
        .then(() => RSP.diagram.mount('diagram-42', descriptor));
    """);
```

### CacheMode Enum

```java
public enum CacheMode {
    NO_CACHE,      // Execute every time
    CACHE,         // Cache by script hash, execute once per session
    CACHE_GLOBAL   // Cache globally across sessions (localStorage)
}
```

---

## On-Demand CSS Loading

CSS can also be pushed on demand when a component needs it.

### Enhanced API for CSS

```java
public interface ClientResources {

    // Load CSS by URL (cached by URL)
    CompletableFuture<Void> ensureStylesheet(String url);

    // Load CSS with integrity check
    CompletableFuture<Void> ensureStylesheet(String url, String integrityHash);

    // Inject inline CSS (cached by hash)
    CompletableFuture<Void> injectCss(String cssId, String css, CacheMode cacheMode);

    // Remove previously injected CSS
    CompletableFuture<Void> removeCss(String cssId);
}
```

### Client-Side CSS Implementation

```javascript
class Client {
    constructor() {
        this.loadedStylesheets = new Set();
        this.injectedStyles = new Map();  // cssId -> <style> element
    }

    // Load external stylesheet
    ensureStylesheet(descriptor, url, integrityHash) {
        // Check if already loaded
        if (this.loadedStylesheets.has(url)) {
            this.callback(CallbackType.EVALJS_RESPONSE, `${descriptor}:0`, null);
            return;
        }

        const link = document.createElement('link');
        link.rel = 'stylesheet';
        link.href = url;
        if (integrityHash) {
            link.integrity = integrityHash;
            link.crossOrigin = 'anonymous';
        }

        link.onload = () => {
            this.loadedStylesheets.add(url);
            this.callback(CallbackType.EVALJS_RESPONSE, `${descriptor}:0`, null);
        };

        link.onerror = () => {
            this.callback(CallbackType.EVALJS_RESPONSE, `${descriptor}:1`, `Failed to load ${url}`);
        };

        document.head.appendChild(link);
    }

    // Inject inline CSS
    injectCss(descriptor, cssId, css, cacheMode) {
        const cacheKey = cssId || this.hashCode(css);

        // Check cache
        if (cacheMode === 'CACHE' && this.injectedStyles.has(cacheKey)) {
            this.callback(CallbackType.EVALJS_RESPONSE, `${descriptor}:0`, null);
            return;
        }

        if (cacheMode === 'CACHE_GLOBAL' && localStorage.getItem(`rsp_css_${cacheKey}`)) {
            // Check if style element exists in DOM
            if (document.getElementById(`rsp-style-${cacheKey}`)) {
                this.callback(CallbackType.EVALJS_RESPONSE, `${descriptor}:0`, null);
                return;
            }
        }

        // Create and inject style element
        const style = document.createElement('style');
        style.id = `rsp-style-${cacheKey}`;
        style.textContent = css;
        document.head.appendChild(style);

        // Cache
        this.injectedStyles.set(cacheKey, style);
        if (cacheMode === 'CACHE_GLOBAL') {
            localStorage.setItem(`rsp_css_${cacheKey}`, 'true');
        }

        this.callback(CallbackType.EVALJS_RESPONSE, `${descriptor}:0`, null);
    }

    // Remove injected CSS
    removeCss(descriptor, cssId) {
        const style = this.injectedStyles.get(cssId);
        if (style) {
            style.remove();
            this.injectedStyles.delete(cssId);
        }
        this.callback(CallbackType.EVALJS_RESPONSE, `${descriptor}:0`, null);
    }
}
```

### ClientLibrary with CSS

```java
public interface ClientLibrary {
    String name();
    String cdnUrl();
    String integrityHash();
    String globalObject();

    // NEW: CSS dependencies
    default List<StyleSheet> stylesheets() {
        return List.of();
    }
}

public record StyleSheet(String url, String integrityHash) {}

public record LeafletLibrary() implements ClientLibrary {
    @Override public String name() { return "leaflet"; }
    @Override public String cdnUrl() {
        return "https://unpkg.com/leaflet@1.9.4/dist/leaflet.js";
    }
    @Override public String integrityHash() { return "sha384-..."; }
    @Override public String globalObject() { return "L"; }

    @Override
    public List<StyleSheet> stylesheets() {
        return List.of(new StyleSheet(
            "https://unpkg.com/leaflet@1.9.4/dist/leaflet.css",
            "sha384-..."
        ));
    }
}
```

### Component-Scoped CSS

For component-specific styles, inject CSS when component mounts and optionally remove on unmount:

```java
public class MapView extends ClientView {

    private static final String MAP_STYLES = """
        .map-container {
            width: 100%;
            height: 400px;
            border-radius: 8px;
            overflow: hidden;
        }
        .map-container .leaflet-control-zoom {
            border: none;
            box-shadow: 0 2px 6px rgba(0,0,0,0.3);
        }
        .marker-popup {
            font-family: system-ui, sans-serif;
        }
        """;

    private String cssId;

    @Override
    public void onAfterRendered(ClientViewState state,
                               Subscriber subscriber,
                               Consumer<Command> commandsEnqueue,
                               StateUpdate<ClientViewState> stateUpdate) {

        // Inject component CSS (cached per session)
        cssId = "map-view-" + state.componentId();
        commandsEnqueue.accept(new RemoteCommand.EvalJs(0,
            String.format("RSP.resources.injectCss('%s', `%s`, 'CACHE')",
                cssId, MAP_STYLES)
        ));

        // Then initialize component...
        super.onAfterRendered(state, subscriber, commandsEnqueue, stateUpdate);
    }

    @Override
    public void onUnmounted(ComponentCompositeKey componentId, ClientViewState state) {
        // Optionally remove component-specific CSS
        // (usually not needed if CSS is shared across instances)
        // commandsEnqueue.accept(new RemoteCommand.EvalJs(0,
        //     String.format("RSP.resources.removeCss('%s')", cssId)
        // ));

        super.onUnmounted(componentId, state);
    }
}
```

### CSS Loading Order

When a component needs both CSS and JS:

```java
private void initializeClientComponent(ClientViewState state) {
    ClientLibrary lib = state.library();

    // Load CSS/JS and initialize after all resources are ready.
    String resourcePromises = Stream.concat(
        lib.stylesheets().stream()
            .map(css -> "RSP.resources.ensureStylesheet('" + css.url() + "', '" + css.integrityHash() + "')"),
        Stream.of("RSP.resources.ensureScript('" + lib.cdnUrl() + "', '" + lib.integrityHash() + "')")
    ).collect(Collectors.joining(",\n            "));

    String initScript = String.format("""
        Promise.all([
            %s
        ]).then(() => {
            const container = document.getElementById('client-%s');
            const component = new window.%s(container, %s, (event, data) => {
                RSP.callbacks.emit('clientEvent', {
                    componentId: '%s',
                    event,
                    data
                });
            });
            RSP.capabilities.set('%s', component);
        });
        """,
        resourcePromises,
        state.componentId(),
        state.clientComponentName(),
        serializeConfig(state.config()),
        state.componentId(),
        state.componentId()
    );
    commandsEnqueue.accept(new RemoteCommand.EvalJs(0, initScript));
}
```

### Theming Support

Dynamic theme switching via CSS custom properties:

```java
public class ThemeService {

    private static final String LIGHT_THEME = """
        :root {
            --bg-primary: #ffffff;
            --bg-secondary: #f5f5f5;
            --text-primary: #333333;
            --text-secondary: #666666;
            --accent: #0066cc;
        }
        """;

    private static final String DARK_THEME = """
        :root {
            --bg-primary: #1e1e1e;
            --bg-secondary: #2d2d2d;
            --text-primary: #ffffff;
            --text-secondary: #cccccc;
            --accent: #4da6ff;
        }
        """;

    public void setTheme(String theme, Consumer<Command> commandsEnqueue) {
        String css = theme.equals("dark") ? DARK_THEME : LIGHT_THEME;

        // Replace theme CSS (same ID = replaces existing)
        commandsEnqueue.accept(new RemoteCommand.EvalJs(0,
            String.format("RSP.resources.injectCss('theme', `%s`, 'NO_CACHE')", css)
        ));
    }
}
```

---

## Dynamic CSS Generation

Since CSS is pushed as strings, it can be generated on-the-fly from rules, DSLs, or data.

### Type-Safe CSS DSL

```java
public class Css {

    public static String rule(String selector, Property... properties) {
        return selector + " {\n" +
            Arrays.stream(properties)
                .map(p -> "    " + p.name + ": " + p.value + ";")
                .collect(Collectors.joining("\n")) +
            "\n}";
    }

    public static Property prop(String name, String value) {
        return new Property(name, value);
    }

    public record Property(String name, String value) {}

    // Convenience methods
    public static Property color(String value) { return prop("color", value); }
    public static Property background(String value) { return prop("background", value); }
    public static Property padding(String value) { return prop("padding", value); }
    public static Property margin(String value) { return prop("margin", value); }
    public static Property display(String value) { return prop("display", value); }
    public static Property flexDirection(String value) { return prop("flex-direction", value); }
    public static Property gap(String value) { return prop("gap", value); }
    public static Property width(String value) { return prop("width", value); }
    public static Property height(String value) { return prop("height", value); }
    public static Property border(String value) { return prop("border", value); }
    public static Property borderRadius(String value) { return prop("border-radius", value); }
    public static Property boxShadow(String value) { return prop("box-shadow", value); }
    public static Property fontSize(String value) { return prop("font-size", value); }
    public static Property fontWeight(String value) { return prop("font-weight", value); }
}

// Usage
import static Css.*;

String css = String.join("\n",
    rule(".card",
        display("flex"),
        flexDirection("column"),
        padding("1rem"),
        borderRadius("8px"),
        boxShadow("0 2px 8px rgba(0,0,0,0.1)")
    ),
    rule(".card-title",
        fontSize("1.25rem"),
        fontWeight("600"),
        color("var(--text-primary)")
    ),
    rule(".card-body",
        padding("0.5rem 0"),
        color("var(--text-secondary)")
    )
);
```

### Fluent CSS Builder

```java
public class CssBuilder {
    private final StringBuilder css = new StringBuilder();
    private String currentSelector;
    private final List<String> properties = new ArrayList<>();

    public CssBuilder select(String selector) {
        flushRule();
        this.currentSelector = selector;
        return this;
    }

    public CssBuilder prop(String name, String value) {
        properties.add(name + ": " + value);
        return this;
    }

    // Convenience methods with chaining
    public CssBuilder color(String v) { return prop("color", v); }
    public CssBuilder bg(String v) { return prop("background", v); }
    public CssBuilder padding(String v) { return prop("padding", v); }
    public CssBuilder margin(String v) { return prop("margin", v); }
    public CssBuilder display(String v) { return prop("display", v); }
    public CssBuilder flex() { return display("flex"); }
    public CssBuilder grid() { return display("grid"); }
    public CssBuilder flexDir(String v) { return prop("flex-direction", v); }
    public CssBuilder gap(String v) { return prop("gap", v); }
    public CssBuilder w(String v) { return prop("width", v); }
    public CssBuilder h(String v) { return prop("height", v); }
    public CssBuilder rounded(String v) { return prop("border-radius", v); }
    public CssBuilder shadow(String v) { return prop("box-shadow", v); }
    public CssBuilder font(String size, String weight) {
        return prop("font-size", size).prop("font-weight", weight);
    }

    // Pseudo-classes and states
    public CssBuilder hover() {
        flushRule();
        currentSelector = currentSelector + ":hover";
        return this;
    }

    public CssBuilder focus() {
        flushRule();
        currentSelector = currentSelector + ":focus";
        return this;
    }

    public CssBuilder child(String selector) {
        flushRule();
        currentSelector = currentSelector + " " + selector;
        return this;
    }

    private void flushRule() {
        if (currentSelector != null && !properties.isEmpty()) {
            css.append(currentSelector).append(" {\n");
            properties.forEach(p -> css.append("    ").append(p).append(";\n"));
            css.append("}\n");
            properties.clear();
        }
    }

    public String build() {
        flushRule();
        return css.toString();
    }
}

// Usage
String css = new CssBuilder()
    .select(".btn")
        .padding("0.5rem 1rem")
        .rounded("4px")
        .bg("var(--accent)")
        .color("white")
        .font("0.875rem", "500")
    .hover()
        .bg("var(--accent-dark)")
    .select(".btn-secondary")
        .bg("transparent")
        .color("var(--accent)")
        .prop("border", "1px solid var(--accent)")
    .hover()
        .bg("var(--accent)")
        .color("white")
    .build();
```

### Data-Driven CSS Generation

Generate CSS from data models:

```java
public class DataDrivenCss {

    // Generate grid layout from column definitions
    public static String gridFromSchema(ListSchema schema) {
        int columnCount = schema.columns().size();

        return new CssBuilder()
            .select(".data-grid")
                .grid()
                .prop("grid-template-columns", "repeat(" + columnCount + ", 1fr)")
                .gap("1px")
                .bg("var(--border-color)")
            .select(".data-grid-header")
                .bg("var(--bg-secondary)")
                .padding("0.75rem")
                .font("0.75rem", "600")
                .prop("text-transform", "uppercase")
            .select(".data-grid-cell")
                .bg("var(--bg-primary)")
                .padding("0.75rem")
            .build();
    }

    // Generate color palette from brand colors
    public static String paletteFromColors(Map<String, String> colors) {
        StringBuilder css = new StringBuilder(":root {\n");
        colors.forEach((name, value) -> {
            css.append("    --").append(name).append(": ").append(value).append(";\n");
            // Generate variants
            css.append("    --").append(name).append("-light: ").append(lighten(value, 20)).append(";\n");
            css.append("    --").append(name).append("-dark: ").append(darken(value, 20)).append(";\n");
        });
        css.append("}\n");
        return css.toString();
    }

    // Generate spacing utilities
    public static String spacingUtilities(int maxSize) {
        CssBuilder builder = new CssBuilder();
        for (int i = 0; i <= maxSize; i++) {
            String value = (i * 0.25) + "rem";
            builder.select(".p-" + i).padding(value);
            builder.select(".m-" + i).margin(value);
            builder.select(".px-" + i).prop("padding-left", value).prop("padding-right", value);
            builder.select(".py-" + i).prop("padding-top", value).prop("padding-bottom", value);
            builder.select(".mx-" + i).prop("margin-left", value).prop("margin-right", value);
            builder.select(".my-" + i).prop("margin-top", value).prop("margin-bottom", value);
        }
        return builder.build();
    }
}
```

### Component-Based CSS (CSS-in-Java)

Define styles alongside component logic:

```java
public abstract class StyledComponent<S> extends Component<S> {

    // Override to provide component styles
    protected CssBuilder styles() {
        return new CssBuilder();
    }

    // Unique scope prefix for this component
    private final String scopeId = "sc-" + Integer.toHexString(System.identityHashCode(this));

    @Override
    public void onAfterRendered(S state, Subscriber subscriber,
                               Consumer<Command> commandsEnqueue,
                               StateUpdate<S> stateUpdate) {
        // Inject scoped CSS
        String css = scopeStyles(styles().build());
        if (!css.isEmpty()) {
            commandsEnqueue.accept(new RemoteCommand.EvalJs(0,
                String.format("RSP.resources.injectCss('%s', `%s`, 'CACHE')", scopeId, css)
            ));
        }
    }

    // Prefix all selectors with scope ID
    private String scopeStyles(String css) {
        return css.replaceAll("(?m)^([.#a-zA-Z])", "." + scopeId + " $1");
    }

    // Wrap view with scope class
    protected Definition scoped(Definition... children) {
        return div(attr("class", scopeId), children);
    }
}

// Usage
public class CardComponent extends StyledComponent<CardState> {

    @Override
    protected CssBuilder styles() {
        return new CssBuilder()
            .select(".card")
                .flex().flexDir("column")
                .padding("1.5rem")
                .rounded("12px")
                .bg("var(--bg-primary)")
                .shadow("0 4px 12px rgba(0,0,0,0.1)")
            .select(".card-header")
                .flex().prop("justify-content", "space-between")
                .margin("0 0 1rem 0")
            .select(".card-title")
                .font("1.25rem", "600")
                .color("var(--text-primary)")
            .select(".card-actions")
                .flex().gap("0.5rem");
    }

    @Override
    public ComponentView<CardState> componentView() {
        return newState -> state -> scoped(
            div(attr("class", "card"),
                div(attr("class", "card-header"),
                    h2(attr("class", "card-title"), text(state.title())),
                    div(attr("class", "card-actions"),
                        button(text("Edit")),
                        button(text("Delete"))
                    )
                ),
                div(attr("class", "card-body"),
                    text(state.content())
                )
            )
        );
    }
}
```

### Conditional/Responsive CSS

Generate responsive or conditional styles:

```java
public class ResponsiveCss {

    public static String responsive(Map<String, CssBuilder> breakpoints) {
        StringBuilder css = new StringBuilder();

        // Mobile first (no media query)
        if (breakpoints.containsKey("base")) {
            css.append(breakpoints.get("base").build());
        }

        // Breakpoints
        Map<String, String> queries = Map.of(
            "sm", "(min-width: 640px)",
            "md", "(min-width: 768px)",
            "lg", "(min-width: 1024px)",
            "xl", "(min-width: 1280px)"
        );

        queries.forEach((name, query) -> {
            if (breakpoints.containsKey(name)) {
                css.append("@media ").append(query).append(" {\n");
                css.append(breakpoints.get(name).build());
                css.append("}\n");
            }
        });

        return css.toString();
    }
}

// Usage
String css = ResponsiveCss.responsive(Map.of(
    "base", new CssBuilder()
        .select(".container").w("100%").padding("1rem"),
    "md", new CssBuilder()
        .select(".container").w("768px").margin("0 auto"),
    "lg", new CssBuilder()
        .select(".container").w("1024px")
));
```

### CSS from Configuration

Generate CSS from user/admin configuration:

```java
public class ConfigurableCss {

    public record BrandConfig(
        String primaryColor,
        String secondaryColor,
        String fontFamily,
        String borderRadius,
        String spacing
    ) {}

    public static String fromBrandConfig(BrandConfig config) {
        return new CssBuilder()
            .select(":root")
                .prop("--primary", config.primaryColor())
                .prop("--secondary", config.secondaryColor())
                .prop("--font-family", config.fontFamily())
                .prop("--radius", config.borderRadius())
                .prop("--spacing", config.spacing())
            .select("body")
                .prop("font-family", "var(--font-family)")
            .select(".btn-primary")
                .bg("var(--primary)")
                .rounded("var(--radius)")
                .padding("var(--spacing)")
            .select(".btn-secondary")
                .bg("var(--secondary)")
                .rounded("var(--radius)")
                .padding("var(--spacing)")
            .build();
    }
}

// Load from database/config and push to client
BrandConfig brand = configService.getBrandConfig(tenantId);
String css = ConfigurableCss.fromBrandConfig(brand);
commandsEnqueue.accept(new RemoteCommand.EvalJs(0,
    String.format("RSP.resources.injectCss('brand', `%s`, 'NO_CACHE')", css)
));
```

### Summary: CSS Generation Approaches

| Approach             | Use Case                 | Example                            |
|----------------------|--------------------------|------------------------------------|
| **Static strings**   | Simple, known styles     | Template literals                  |
| **Type-safe DSL**    | Compile-time safety      | `rule(".btn", color("red"))`       |
| **Fluent builder**   | Complex, readable styles | `new CssBuilder().select().prop()` |
| **Data-driven**      | Schema-based layouts     | Grid from ListSchema               |
| **Component-scoped** | Encapsulated styles      | StyledComponent base class         |
| **Responsive**       | Breakpoint variations    | `ResponsiveCss.responsive()`       |
| **Configuration**    | Multi-tenant, user prefs | Brand colors from DB               |

### Resource Loading API

```java
public interface ClientResources {

    /**
     * Existing low-level escape hatch. Capability authors may use it; application
     * code should prefer typed Java wrappers.
     */
    CompletableFuture<JsonDataType> evalJs(String js);

    /**
     * Load a JavaScript file once per page session.
     */
    CompletableFuture<Void> ensureScript(String url);

    /**
     * Load a JavaScript file once, with an optional integrity hash.
     */
    CompletableFuture<Void> ensureScript(String url, String integrityHash);

    /**
     * Load a stylesheet once per page session.
     */
    CompletableFuture<Void> ensureStylesheet(String url);

    /**
     * Inject or replace inline CSS under a stable id.
     */
    CompletableFuture<Void> injectCss(String cssId, String css, CacheMode cacheMode);
}
```

`evalJs(code, CacheMode)` is deliberately not the primary API. Caching large raw
code strings makes versioning and debugging harder. Prefer serving framework or
application capability modules as static resources and using `evalJs` only to
call the loader and mount/update the component instance.

### Client-Side Implementation Sketch

```javascript
window.RSP = window.RSP || {};
RSP.resources = RSP.resources || (() => {
    const scripts = new Map();
    const stylesheets = new Map();
    const inlineStyles = new Map();

    function ensureScript(url, integrityHash) {
        if (scripts.has(url)) {
            return scripts.get(url);
        }

        const promise = new Promise((resolve, reject) => {
            const script = document.createElement('script');
            script.src = url;
            if (integrityHash) {
                script.integrity = integrityHash;
                script.crossOrigin = 'anonymous';
            }
            script.onload = resolve;
            script.onerror = () => reject(new Error(`Failed to load ${url}`));
            document.head.appendChild(script);
        });

        scripts.set(url, promise);
        return promise;
    }

    function ensureStylesheet(url, integrityHash) {
        if (stylesheets.has(url)) {
            return stylesheets.get(url);
        }

        const promise = new Promise((resolve, reject) => {
            const link = document.createElement('link');
            link.rel = 'stylesheet';
            link.href = url;
            if (integrityHash) {
                link.integrity = integrityHash;
                link.crossOrigin = 'anonymous';
            }
            link.onload = resolve;
            link.onerror = () => reject(new Error(`Failed to load ${url}`));
            document.head.appendChild(link);
        });

        stylesheets.set(url, promise);
        return promise;
    }

    function injectCss(cssId, css, cacheMode = 'NO_CACHE') {
        let style = inlineStyles.get(cssId) || document.getElementById(cssId);
        if (!style) {
            style = document.createElement('style');
            style.id = cssId;
            document.head.appendChild(style);
            inlineStyles.set(cssId, style);
        }
        style.textContent = css;
        return Promise.resolve();
    }

    function removeCss(cssId) {
        const style = inlineStyles.get(cssId) || document.getElementById(cssId);
        if (style) {
            style.remove();
        }
        inlineStyles.delete(cssId);
        return Promise.resolve();
    }

    return { ensureScript, ensureStylesheet, injectCss, removeCss };
})();
```

### Semantic Callback Transport

Interactive bridges should report intent, not raw browser events. The browser
should send compact semantic payloads:

```javascript
RSP.callbacks.emit('diagram.nodeMoved', {
    componentId: 'diagram-42',
    nodeId: 'task-7',
    x: 320,
    y: 180
});
```

Target client-side shape:

```javascript
RSP.callbacks = RSP.callbacks || {
    emit(name, payload) {
        // Sends a CUSTOM_CALLBACK WebSocket message with JSON payload.
        // The server decoder routes it to the component event bus.
        RSP.__bridge.sendCustomCallback(name, payload);
    }
};
```

Target server-side behavior:

```text
CUSTOM_CALLBACK(name, payload)
  -> decode payload as JsonDataType.Object
  -> locate the component/contract by componentId when present
  -> publish a typed ComponentEventNotification or ClientEventNotification
  -> invoke the Java handler registered by the capability/contract
```

This replaces ad hoc callback strings and avoids exposing JavaScript callback
plumbing to application code.

### Capability Instance Registry

The client needs a small registry for mounted bridge instances:

```javascript
RSP.capabilities = RSP.capabilities || (() => {
    const instances = new Map();

    return {
        set(id, instance) {
            this.destroy(id);
            instances.set(id, instance);
        },
        get(id) {
            return instances.get(id);
        },
        update(id, config) {
            const instance = instances.get(id);
            if (instance && instance.updateConfig) {
                instance.updateConfig(config);
            }
        },
        destroy(id) {
            const instance = instances.get(id);
            if (instance && instance.destroy) {
                instance.destroy();
            }
            instances.delete(id);
        }
    };
})();
```

---

## Library Registry

Declare required libraries per module or contract:

```java
public interface ClientLibrary {
    String name();           // "d3", "leaflet", "monaco"
    String cdnUrl();         // CDN URL
    String integrityHash();  // SRI hash (optional)
    String globalObject();   // "d3", "L", "monaco"
}

public record D3Library() implements ClientLibrary {
    @Override public String name() { return "d3"; }
    @Override public String cdnUrl() {
        return "https://cdn.jsdelivr.net/npm/d3@7/dist/d3.min.js";
    }
    @Override public String integrityHash() { return "sha384-..."; }
    @Override public String globalObject() { return "d3"; }
}

public record LeafletLibrary() implements ClientLibrary {
    @Override public String name() { return "leaflet"; }
    @Override public String cdnUrl() {
        return "https://unpkg.com/leaflet@1.9.4/dist/leaflet.js";
    }
    @Override public String integrityHash() { return "sha384-..."; }
    @Override public String globalObject() { return "L"; }
}
```

### Module Declaration

```java
public interface Module {
    // Existing
    List<ViewPlacement> views();
    List<NotificationContract> notifications();
    List<ActionContract> actions();

    // New: required client libraries
    default List<ClientLibrary> clientLibraries() {
        return List.of();
    }
}

// Usage
public class MapsModule implements Module {
    @Override
    public List<ClientLibrary> clientLibraries() {
        return List.of(new LeafletLibrary());
    }
}
```

---

## ClientViewContract

Base contract for views that require client-side libraries:

```java
public abstract class ClientViewContract extends ViewContract {

    protected ClientViewContract(ComponentContext context) {
        super(context);
    }

    /**
     * Required client library for this view.
     */
    public abstract ClientLibrary library();

    /**
     * Configuration passed to client-side component.
     * Serialized to JSON.
     */
    public abstract Map<String, Object> clientConfig();

    /**
     * Client-side component class name.
     * Must be registered in client bridge.
     */
    public abstract String clientComponentName();

    /**
     * Events this component can receive from client.
     */
    public List<String> clientEvents() {
        return List.of();
    }
}
```

### Example: Leaflet Map Contract

```java
public class MapViewContract extends ClientViewContract {

    public record MapConfig(
        double lat,
        double lng,
        int zoom,
        List<Marker> markers
    ) {}

    public record Marker(String id, double lat, double lng, String popup) {}

    private final MapConfig config;

    public MapViewContract(ComponentContext context) {
        super(context);
        // Load config from context or service
        this.config = new MapConfig(51.505, -0.09, 13, List.of());
    }

    @Override
    public ClientLibrary library() {
        return new LeafletLibrary();
    }

    @Override
    public Map<String, Object> clientConfig() {
        return Map.of(
            "center", Map.of("lat", config.lat, "lng", config.lng),
            "zoom", config.zoom,
            "markers", config.markers
        );
    }

    @Override
    public String clientComponentName() {
        return "LeafletMapBridge";
    }

    @Override
    public List<String> clientEvents() {
        return List.of("markerClick", "mapClick", "zoomChange");
    }
}
```

---

## ClientView Component

UI component that renders client-library views:

```java
public class ClientView extends Component<ClientView.ClientViewState> {

    protected Consumer<Command> commandsEnqueue;
    protected ElementRef containerRef;

    public record ClientViewState(
        String componentId,
        ClientLibrary library,
        Map<String, Object> config,
        String clientComponentName,
        boolean initialized
    ) {}

    @Override
    public ComponentStateSupplier<ClientViewState> initStateSupplier() {
        return (_, context) -> {
            ClientViewContract contract =
                (ClientViewContract) context.get(ContextKeys.VIEW_CONTRACT);

            return new ClientViewState(
                UUID.randomUUID().toString(),
                contract.library(),
                contract.clientConfig(),
                contract.clientComponentName(),
                false
            );
        };
    }

    @Override
    public ComponentView<ClientViewState> componentView() {
        containerRef = createElementRef();

        return newState -> state -> div(
            attr("class", "client-view-container"),
            div(
                ref(containerRef),
                attr("id", "client-" + state.componentId()),
                attr("class", "client-view-content"),
                attr("data-component", state.clientComponentName()),
                attr("data-config", serializeConfig(state.config()))
            )
        );
    }

    @Override
    public void onAfterRendered(ClientViewState state,
                               Subscriber subscriber,
                               Consumer<Command> commandsEnqueue,
                               StateUpdate<ClientViewState> stateUpdate) {
        this.commandsEnqueue = commandsEnqueue;

        if (!state.initialized()) {
            initializeClientComponent(state, stateUpdate);
        }
    }

    private void initializeClientComponent(ClientViewState state,
                                           StateUpdate<ClientViewState> stateUpdate) {
        String initComponent = String.format("""
            (function() {
                RSP.resources.ensureScript('%s', '%s').then(() => {
                    const container = document.getElementById('client-%s');
                    const config = JSON.parse(container.dataset.config);
                    const component = new window.%s(container, config, (event, data) => {
                        RSP.callbacks.emit('clientEvent', {
                            componentId: '%s',
                            event,
                            data
                        });
                    });
                    RSP.capabilities.set('%s', component);
                });
            })()
            """,
            state.library().cdnUrl(),
            state.library().integrityHash(),
            state.componentId(),
            state.clientComponentName(),
            state.componentId(),
            state.componentId()
        );

        commandsEnqueue.accept(new RemoteCommand.EvalJs(0, initComponent));

        // Mark as initialized
        stateUpdate.setState(new ClientViewState(
            state.componentId(),
            state.library(),
            state.config(),
            state.clientComponentName(),
            true
        ));
    }

    private String serializeConfig(Map<String, Object> config) {
        // JSON serialization owned by the framework/capability layer.
        return serializeToJson(config);
    }
}
```

---

## Client-Side Bridge Pattern

### Bridge Interface

```javascript
// Base class for all client library bridges
class ClientBridge {
    constructor(container, config, eventCallback) {
        this.container = container;
        this.config = config;
        this.eventCallback = eventCallback;
    }

    // Called when server state updates
    updateConfig(newConfig) {
        throw new Error('updateConfig must be implemented');
    }

    // Called when component unmounts
    destroy() {
        throw new Error('destroy must be implemented');
    }

    // Send event to server
    emit(eventName, data) {
        this.eventCallback(eventName, data);
    }
}
```

### Leaflet Bridge Example

```javascript
class LeafletMapBridge extends ClientBridge {
    constructor(container, config, eventCallback) {
        super(container, config, eventCallback);

        // Initialize Leaflet map
        this.map = L.map(container).setView(
            [config.center.lat, config.center.lng],
            config.zoom
        );

        // Add tile layer
        L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
            attribution: '© OpenStreetMap contributors'
        }).addTo(this.map);

        // Add markers
        this.markers = {};
        this.addMarkers(config.markers || []);

        // Bind events
        this.map.on('click', (e) => {
            this.emit('mapClick', { lat: e.latlng.lat, lng: e.latlng.lng });
        });

        this.map.on('zoomend', () => {
            this.emit('zoomChange', { zoom: this.map.getZoom() });
        });
    }

    addMarkers(markers) {
        markers.forEach(m => {
            const marker = L.marker([m.lat, m.lng])
                .addTo(this.map)
                .bindPopup(m.popup);

            marker.on('click', () => {
                this.emit('markerClick', { markerId: m.id });
            });

            this.markers[m.id] = marker;
        });
    }

    updateConfig(newConfig) {
        // Update view
        if (newConfig.center) {
            this.map.setView(
                [newConfig.center.lat, newConfig.center.lng],
                newConfig.zoom || this.map.getZoom()
            );
        }

        // Update markers
        if (newConfig.markers) {
            // Remove old markers
            Object.values(this.markers).forEach(m => m.remove());
            this.markers = {};
            // Add new markers
            this.addMarkers(newConfig.markers);
        }
    }

    destroy() {
        this.map.remove();
    }
}

// Register bridge
window.LeafletMapBridge = LeafletMapBridge;
```

### D3 Chart Bridge Example

```javascript
class D3ChartBridge extends ClientBridge {
    constructor(container, config, eventCallback) {
        super(container, config, eventCallback);

        this.svg = d3.select(container)
            .append('svg')
            .attr('width', config.width || 800)
            .attr('height', config.height || 400);

        this.renderChart(config.data, config.type);
    }

    renderChart(data, type) {
        // Clear existing
        this.svg.selectAll('*').remove();

        switch (type) {
            case 'bar':
                this.renderBarChart(data);
                break;
            case 'line':
                this.renderLineChart(data);
                break;
            case 'pie':
                this.renderPieChart(data);
                break;
        }
    }

    renderBarChart(data) {
        const margin = { top: 20, right: 20, bottom: 30, left: 40 };
        const width = +this.svg.attr('width') - margin.left - margin.right;
        const height = +this.svg.attr('height') - margin.top - margin.bottom;

        const x = d3.scaleBand().range([0, width]).padding(0.1);
        const y = d3.scaleLinear().range([height, 0]);

        const g = this.svg.append('g')
            .attr('transform', `translate(${margin.left},${margin.top})`);

        x.domain(data.map(d => d.label));
        y.domain([0, d3.max(data, d => d.value)]);

        g.selectAll('.bar')
            .data(data)
            .enter().append('rect')
            .attr('class', 'bar')
            .attr('x', d => x(d.label))
            .attr('y', d => y(d.value))
            .attr('width', x.bandwidth())
            .attr('height', d => height - y(d.value))
            .attr('fill', 'steelblue')
            .on('click', (event, d) => {
                this.emit('barClick', { label: d.label, value: d.value });
            });
    }

    updateConfig(newConfig) {
        if (newConfig.data) {
            this.renderChart(newConfig.data, newConfig.type || 'bar');
        }
    }

    destroy() {
        this.svg.remove();
    }
}

window.D3ChartBridge = D3ChartBridge;
```

---

## Common Interaction Capabilities

Many rich browser features share the same gesture and animation plumbing. The
framework should provide reusable Java capability APIs backed by one common JS
interaction runtime.

### Drag and Drop

Use for sortable lists, kanban cards, palette items, draggable diagram nodes,
resizable panels, and file-drop surfaces.

Java-facing API:

```java
div(
    interaction()
        .draggable()
        .dropGroup("kanban-card")
        .commitOnEnd(KanbanEvents.CARD_DROPPED),
    text(card.title())
)
```

Client responsibilities:

- pointer capture and cancellation
- live transform updates during drag
- optional drag ghost or placeholder
- drop target hit testing
- throttled preview events if requested
- one semantic commit event on pointer-up/drop

Server responsibilities:

- deciding whether the drop is valid
- updating persisted order/location
- authorizing cross-list/cross-zone moves
- pushing the accepted state back to the client

### Animation

Animations should be declarative from Java and executed by the browser. The
default technique should be FLIP for DOM moved by server patches:

```java
ul(
    animate()
        .move("flip")
        .enter("fade-scale")
        .leave("fade")
        .durationMillis(160),
    of(items.stream().map(this::renderItem))
)
```

The JS runtime measures old bounds before a patch, applies the server patch,
then animates from old to new bounds. It must respect reduced-motion settings
and avoid blocking server state updates.

### Pan and Zoom

Pan/zoom is a common primitive for diagrams, maps, large canvases, image
inspectors, and graph dashboards. The client should own the live viewport
transform; Java can decide whether viewport changes matter to the application.

Recommended event flow:

- client handles wheel/pinch/drag pan locally
- client optionally emits throttled `viewportChanged`
- server stores viewport only when it affects URLs, persistence, collaboration,
  or data windowing

### Selection and Lasso

Selection can be local while the pointer is moving, then committed to Java when
the selection changes:

```java
nodeGraph()
    .selectable(true)
    .lasso(true)
    .onSelectionChanged(DiagramEvents.SELECTION_CHANGED);
```

The client may hold hover state, focus rings, rubber-band geometry, and drag
previews. The server owns selected ids if selection drives panels, permissions,
commands, or persistence.

## Graph Capability Cases

Graphs are a family of capabilities rather than one feature.

### Static Graphs

For small, read-only graphs, Java can render SVG directly through the DSL. No
bridge is required unless the graph needs pan/zoom, tooltips, or animations.

```java
svgGraph()
    .nodes(nodes)
    .edges(edges)
    .layout(Layout.LEFT_TO_RIGHT);
```

Limitations:

- large SVG trees can make server diffing expensive
- every visual change goes through the server render/diff path
- no smooth drag/pan/zoom without client-side assistance

### Live Graphs and Charts

Use for metrics, traces, dashboards, live dependency status, timelines, and
data streams.

Java API:

```java
liveChart()
    .series("requests", state.requestSamples())
    .window(Duration.ofMinutes(5))
    .appendMode(true)
    .animate(true);
```

Recommended update model:

```text
Java service emits samples
  -> server batches/window-trims them
  -> bridge receives append/update commands
  -> JS draws and animates efficiently
```

Avoid re-rendering thousands of SVG points through normal DOM diffs at high
frequency. For 1-5 Hz updates, normal state/render may be acceptable. For
30-60 FPS visuals, send coarse data deltas and let the client animate.

Client local state may include render buffers, animation frame ids, and the
current visible viewport. Server state remains the canonical data window,
series definitions, permissions, and persisted dashboard settings.

### Interactive Node Graphs and Diagrams

Use for workflows, pipelines, dependency maps, visual rules, state machines, and
node editors.

Java API:

```java
nodeGraph()
    .nodes(state.nodes())
    .edges(state.edges())
    .panZoom(true)
    .draggableNodes(true)
    .connectablePorts(true)
    .onNodeMoved(DiagramEvents.NODE_MOVED)
    .onEdgeCreated(DiagramEvents.EDGE_CREATED)
    .onSelectionChanged(DiagramEvents.SELECTION_CHANGED);
```

Client responsibilities:

- 60 FPS drag preview
- pan/zoom transform
- edge preview while connecting ports
- hit testing nodes/ports/drop zones
- optional local snapping guides
- compact semantic events (`nodeMoved`, `edgeCreated`, `selectionChanged`)

Server responsibilities:

- graph model and persistence
- validating connections and preventing cycles when needed
- authorization
- deterministic layout if required
- deciding accepted positions/edges after a commit

For large graphs, the bridge may need canvas/WebGL, viewport culling, or a
specialized graph library. That is still compatible with this design: the Java
API remains the same while the bridge implementation chooses the rendering
strategy.

### Event Frequency Guidance

| Interaction       | Preferred server event                                |
|-------------------|-------------------------------------------------------|
| Click/select      | immediate semantic event                              |
| Drag node         | `dragStarted` optional, `nodeMoved` on end            |
| Resize            | `resizeCommitted` on end                              |
| Lasso             | optional throttled preview, `selectionChanged` on end |
| Pan/zoom          | local only, or throttled `viewportChanged`            |
| Live chart sample | batched append/update delta                           |
| Animation frame   | never sent to server                                  |

---

## Server-Side Event Handling

### Explicit Event Handler Registration

Following the framework's philosophy of explicit code over annotations, event handlers are registered explicitly via constructor-provided dependencies.

```java
/**
 * Functional interface for client event handlers.
 * Receives the event data as a Map.
 */
@FunctionalInterface
public interface ClientEventHandler {
    void handle(Map<String, Object> data);
}
```

### ClientViewContract with Explicit Handlers

```java
public abstract class ClientViewContract extends ViewContract {

    // Event handlers registered explicitly
    private final Map<String, ClientEventHandler> eventHandlers = new HashMap<>();

    protected ClientViewContract(ComponentContext context) {
        super(context);
    }

    /**
     * Register an event handler for a specific client event.
     * Called by subclasses in their constructor.
     */
    protected void onClientEvent(String eventName, ClientEventHandler handler) {
        eventHandlers.put(eventName, handler);
    }

    /**
     * Handle an incoming client event by dispatching to registered handler.
     */
    public void handleClientEvent(String eventName, Map<String, Object> data) {
        ClientEventHandler handler = eventHandlers.get(eventName);
        if (handler != null) {
            handler.handle(data);
        }
    }

    /**
     * Returns the set of event names this contract handles.
     * Used by ClientView for event routing.
     */
    public Set<String> handledEvents() {
        return eventHandlers.keySet();
    }
}
```

### Example: Map Contract with Explicit Event Handlers

```java
public class InteractiveMapContract extends ClientViewContract {

    private final MapService mapService;

    public InteractiveMapContract(ComponentContext context) {
        super(context);
        this.mapService = context.getRequired(MapService.class);

        // Explicit event handler registration
        onClientEvent("markerClick", this::handleMarkerClick);
        onClientEvent("mapClick", this::handleMapClick);
        onClientEvent("zoomChange", this::handleZoomChange);
    }

    @Override
    public ClientLibrary library() {
        return new LeafletLibrary();
    }

    @Override
    public Map<String, Object> clientConfig() {
        return Map.of(
            "center", Map.of("lat", 51.505, "lng", -0.09),
            "zoom", 13,
            "markers", mapService.getMarkers()
        );
    }

    @Override
    public String clientComponentName() {
        return "LeafletMapBridge";
    }

    private void handleMarkerClick(Map<String, Object> data) {
        String markerId = (String) data.get("markerId");
        // Handle marker click - e.g., show details panel
        mapService.selectMarker(markerId);
    }

    private void handleMapClick(Map<String, Object> data) {
        double lat = (Double) data.get("lat");
        double lng = (Double) data.get("lng");
        // Handle map click - e.g., add new marker
        mapService.addMarker(lat, lng);
    }

    private void handleZoomChange(Map<String, Object> data) {
        int zoom = (Integer) data.get("zoom");
        // Handle zoom change - e.g., load different data density
        mapService.setZoomLevel(zoom);
    }
}

---

## State Synchronization

### Server → Client Updates

When server state changes, push updates to client:

```java
public class ClientView extends Component<ClientViewState> {

    @Override
    public void onUpdated(ComponentCompositeKey componentId,
                         ClientViewState oldState,
                         ClientViewState newState,
                         StateUpdate<ClientViewState> stateUpdate) {

        // Config changed - update client component
        if (!oldState.config().equals(newState.config())) {
            String updateScript = String.format(
                "RSP.capabilities.get('%s').updateConfig(%s)",
                newState.componentId(),
                serializeConfig(newState.config())
            );
            commandsEnqueue.accept(new RemoteCommand.EvalJs(0, updateScript));
        }
    }

    @Override
    public void onUnmounted(ComponentCompositeKey componentId, ClientViewState state) {
        // Cleanup client component
        String destroyScript = String.format(
            "RSP.capabilities.destroy('%s')",
            state.componentId()
        );
        commandsEnqueue.accept(new RemoteCommand.EvalJs(0, destroyScript));
    }
}
```

### Client → Server Events

Events flow through the RSP semantic callback mechanism. This is a target
protocol addition: the browser sends a JSON payload, the server decodes it,
then routes it to the owning component/contract as a component event.

```javascript
// Client sends event
RSP.callbacks.emit('clientEvent', {
    componentId: 'abc123',
    event: 'markerClick',
    data: { markerId: 'marker-1' }
});

// Server decodes CUSTOM_CALLBACK and routes to:
// ClientViewContract.handleClientEvent("markerClick", payload)
```

---

## Typed Library Wrappers (Optional)

For frequently used libraries, provide type-safe wrappers:

### D3 Wrapper

```java
public class D3Builder {
    private final List<String> commands = new ArrayList<>();
    private final ElementRef containerRef;

    public D3Builder(ElementRef containerRef) {
        this.containerRef = containerRef;
    }

    public D3Builder select() {
        commands.add(String.format("d3.select('#%s')", containerRef.id()));
        return this;
    }

    public D3Builder append(String element) {
        commands.add(String.format(".append('%s')", element));
        return this;
    }

    public D3Builder attr(String name, Object value) {
        commands.add(String.format(".attr('%s', %s)", name, serialize(value)));
        return this;
    }

    public D3Builder data(List<?> data) {
        commands.add(String.format(".data(%s)", serialize(data)));
        return this;
    }

    public D3Builder on(String event, String callbackName) {
        commands.add(String.format(
            ".on('%s', (e, d) => RSP.callbacks.emit('%s', d))",
            event, callbackName
        ));
        return this;
    }

    public String build() {
        return String.join("", commands);
    }

    public void execute(Consumer<Command> commandsEnqueue) {
        commandsEnqueue.accept(new RemoteCommand.EvalJs(0, build()));
    }
}

// Usage
new D3Builder(chartRef)
    .select()
    .append("svg")
    .attr("width", 800)
    .attr("height", 400)
    .execute(commandsEnqueue);
```

---

## Integration with Compositions

### UiRegistry Extension

```java
final UiRegistry uiRegistry = new UiRegistry()
    .register(ListViewContract.class, DefaultListView::new)
    .register(EditViewContract.class, DefaultEditView::new)
    // Client library views
    .register(ClientViewContract.class, ClientView::new)
    .register(MapViewContract.class, ClientView::new)
    .register(ChartViewContract.class, ClientView::new);
```

### Module with Client Libraries

```java
public class DashboardModule implements Module {

    @Override
    public List<ViewPlacement> views() {
        return List.of(
            new ViewPlacement(Slot.PRIMARY, DashboardContract.class, DashboardContract::new),
            new ViewPlacement(Slot.SECONDARY, ChartViewContract.class, ChartViewContract::new),
            new ViewPlacement(Slot.BOTTOM_PANEL, MapViewContract.class, MapViewContract::new)
        );
    }

    @Override
    public List<ClientLibrary> clientLibraries() {
        return List.of(
            new D3Library(),
            new LeafletLibrary()
        );
    }
}
```

---

## Summary

| Layer                                 | Purpose                                                        |
|---------------------------------------|----------------------------------------------------------------|
| **Application Java**                  | Pure Java contracts, services, state, and DSL usage            |
| **Java capability API**               | `DiagramView`, `LiveChart`, `DragDrop`, `Animation`, `MapView` |
| **ClientLibrary**                     | Library/resource metadata (URL, hash, global object)           |
| **ClientViewContract**                | Declares config and semantic event handlers                    |
| **ClientView / capability component** | Renders container, loads resources, mounts bridge              |
| **RSP resources/callbacks**           | Shared loader, cache, semantic callback transport              |
| **RSP interaction runtime**           | Drag/drop, pan/zoom, selection, FLIP, lifecycle cleanup        |
| **ClientBridge (JS)**                 | Specific browser renderer/controller for a capability          |
| **ClientEventHandler**                | Functional interface for explicit server-side handlers         |

### Key Features

1. **Pure Java application surface** - App code uses Java APIs, not JavaScript strings
2. **On-demand loading** - Capability modules load only when needed
3. **Shared runtime** - Common resource, callback, gesture, and animation primitives
4. **Explicit event handlers** - Constructor-registered handlers via `onClientEvent()`
5. **Semantic bidirectional sync** - Server state → client config, client intent → server events
6. **Lifecycle management** - Init on mount, update on state change, cleanup on unmount
7. **Bridge pattern** - Consistent API across maps, charts, diagrams, editors, and DnD

---

## Server-Side First Philosophy

Client library integration preserves the framework's **server-side first** architecture.

### Core Principles

| Principle                          | Implementation                                                               |
|------------------------------------|------------------------------------------------------------------------------|
| **Authoritative state on server**  | Domain state (`MapState`, `ChartData`, graph model) lives in Java            |
| **Logic on server**                | Event handlers registered via `onClientEvent()` are server-side              |
| **Config from server**             | `clientConfig()` is computed server-side                                     |
| **Client as renderer/controller**  | Client turns config into pixels and handles browser-native gestures          |
| **Semantic events to server**      | Client reports intent (`nodeMoved`, `markerClick`), not browser noise        |
| **Ephemeral client state allowed** | Hover, drag previews, zoom transforms, animation frames may stay client-side |
| **No authoritative decisions**     | Client does not authorize, persist, validate, or own business decisions      |

### Architecture

```
┌─────────────────────────────────────────────────────────┐
│                        SERVER                           │
│  ┌─────────────┐    ┌─────────────┐    ┌─────────────┐  │
│  │   State     │───>│   Logic     │───>│  Commands   │  │
│  │  (source    │    │ (handlers,  │    │ (evalJs,    │  │
│  │   of truth) │    │  transforms)│    │  updateCfg) │  │
│  └─────────────┘    └─────────────┘    └─────────────┘  │
└────────────────────────────┬────────────────────────────┘
                             │ WebSocket
                             ▼
┌─────────────────────────────────────────────────────────┐
│                        CLIENT                           │
│  ┌─────────────┐    ┌─────────────┐    ┌─────────────┐  │
│  │  Commands   │───>│ Interaction │───>│   DOM       │  │
│  │  (config)   │    │  runtime +  │    │ SVG/Canvas  │  │
│  │             │    │  bridge     │    │             │  │
│  └─────────────┘    └─────────────┘    └─────────────┘  │
│         ▲                                     │         │
│         │       Semantic events               │         │
│         └─────────────────────────────────────┘         │
└─────────────────────────────────────────────────────────┘
```

### Server Pushes updateConfig()

When server state changes, server pushes new config to client:

```java
// Server: state changed (e.g., user added a marker)
// Handler registered via: onClientEvent("addMarker", this::handleAddMarker);
private void handleAddMarker(Map<String, Object> data) {
    double lat = (Double) data.get("lat");
    double lng = (Double) data.get("lng");

    // Server updates state
    Marker newMarker = markerService.create(lat, lng);
    state = state.withMarker(newMarker);

    // Server pushes new config to client
    pushConfigUpdate();
}

private void pushConfigUpdate() {
    Map<String, Object> config = Map.of(
        "center", Map.of("lat", state.lat(), "lng", state.lng()),
        "zoom", state.zoom(),
        "markers", state.markers()  // Now includes new marker
    );

    commandsEnqueue.accept(new RemoteCommand.EvalJs(0,
        String.format("RSP.capabilities.get('%s').updateConfig(%s)",
            componentId,
            serializeToJson(config)
        )
    ));
}
```

```javascript
// Client: just applies what server sent
class LeafletMapBridge {
    updateConfig(config) {
        // No decisions - just render
        this.map.setView([config.center.lat, config.center.lng], config.zoom);
        this.clearMarkers();
        this.renderMarkers(config.markers);
    }
}
```

### What Stays Server-Side

```java
// ALL business logic is server-side:

public class InteractiveMapContract extends ClientViewContract {

    private final MarkerService markerService;
    private final AuthContext authContext;
    private MapState state;

    public InteractiveMapContract(ComponentContext context) {
        super(context);
        this.markerService = context.getRequired(MarkerService.class);
        this.authContext = context.getRequired(AuthContext.class);

        // Explicit event handler registration
        onClientEvent("markerClick", this::handleMarkerClick);
        onClientEvent("deleteMarker", this::handleDeleteMarker);
    }

    // 1. Data access
    @Override
    public Map<String, Object> clientConfig() {
        List<Marker> markers = markerService.findInBounds(state.bounds());
        return Map.of(
            "center", state.center(),
            "zoom", state.zoom(),
            "markers", markers  // Data from server
        );
    }

    // 2. Event handling with authorization
    private void handleMarkerClick(Map<String, Object> data) {
        String markerId = (String) data.get("markerId");

        // Server decides what happens
        if (authContext.canView(markerId)) {
            state = state.withSelected(markerId);
            pushConfigUpdate();
        }
    }

    // 3. Authorization enforcement
    private void handleDeleteMarker(Map<String, Object> data) {
        String markerId = (String) data.get("markerId");

        // Server enforces permissions
        if (!authContext.canDelete(markerId)) {
            return;  // Silently ignore unauthorized action
        }

        markerService.delete(markerId);
        state = state.withoutMarker(markerId);
        pushConfigUpdate();
    }
}
```

### What Goes Client-Side (Rendering and Gestures)

```javascript
// Client bridge renders and handles browser-native gestures.
// It emits semantic events and waits for authoritative server updates.

class LeafletMapBridge extends ClientBridge {
    constructor(container, config, eventCallback) {
        super(container, config, eventCallback);

        // Initialize renderer
        this.map = L.map(container);
        this.applyConfig(config);

        // Bind events → send to server
        this.map.on('click', (e) => {
            // Does NOT decide what happens
            // Just notifies server with a semantic payload
            this.emit('mapClick', { lat: e.latlng.lat, lng: e.latlng.lng });
        });
    }

    // Receive config from server, render it
    updateConfig(config) {
        this.applyConfig(config);
    }

    applyConfig(config) {
        this.map.setView([config.center.lat, config.center.lng], config.zoom);
        this.renderMarkers(config.markers);
        this.renderSelectedMarker(config.selectedMarkerId);
    }
}
```

### Anti-Patterns to Avoid

```javascript
// BAD: authoritative client-side application state
class BadMapBridge {
    onMarkerClick(marker) {
        // DON'T: Make decisions client-side
        this.selectedMarker = marker;         // ❌ Authoritative selection
        this.showPopup(marker);               // ❌ Domain decision
        this.markers = this.markers.filter(   // ❌ Domain filtering
            m => m.visible
        );
    }
}

// GOOD: Server decides everything
class GoodMapBridge {
    onMarkerClick(marker) {
        // Just notify server, wait for updateConfig()
        this.emit('markerClick', { markerId: marker.id });
    }

    updateConfig(config) {
        // Server already decided what's selected, visible, etc.
        this.renderMarkers(config.markers);
        this.highlightMarker(config.selectedMarkerId);
    }
}
```

Allowed client-side state includes gesture and rendering state: current pointer
position, drag preview transform, hover/focus state, animation frame ids,
temporary edge previews, chart drawing buffers, and current pan/zoom transform.
That state must be disposable and reconstructable from server config.

### Responsibility Matrix

| Responsibility                   | Server | Client Library               |
|----------------------------------|--------|------------------------------|
| What markers exist               | ✓      |                              |
| Which marker is selected         | ✓      |                              |
| What popup content shows         | ✓      |                              |
| When to show/hide elements       | ✓      |                              |
| Filter/sort data                 | ✓      |                              |
| Authorization checks             | ✓      |                              |
| How to render a marker           |        | ✓                            |
| How to animate transitions       |        | ✓                            |
| How to handle gestures           |        | ✓ (semantic events → server) |
| Drag/hover/animation frame state |        | ✓                            |
| Caching/performance              |        | ✓ (under server control)     |

### Caching Consistency

Caching is server-controlled:

```java
// Server/capability decides WHAT resources to load
ctx.evalJs("RSP.resources.ensureScript('/res/rsp-graph.js')");

// Server decides WHEN to invalidate
ctx.evalJs("RSP.resources.injectCss('theme', `" + newThemeCss + "`)");

// Client resource cache is mechanical: it avoids duplicate loads but does not
// decide application state or domain behavior.
```

### CSS Generation is Server-Side

Dynamic CSS generation is fully server-side first:

```java
// Server generates CSS based on:
// 1. Server state (user preferences)
String theme = userPrefs.getTheme();

// 2. Server data (schema structure)
int columns = schema.columns().size();

// 3. Server config (tenant branding)
BrandConfig brand = configService.getBrand(tenantId);

String css = new CssBuilder()
    .select(".grid")
    .prop("grid-template-columns", "repeat(" + columns + ", 1fr)")
    .prop("--primary", brand.primaryColor())
    .build();

// Client just applies it through the resource layer
ctx.resources().injectCss("layout", css, CacheMode.CACHE);
```

## New HTML-in-Canvas API

New HTML-in-Canvas API opens up powerful optimization and User Experience (UX) capabilities.

Here is a validation of how this API can be utilized in such a framework, along with new ways to significantly improve the UX for developers and end-users.
1. Off-Main-Thread UI Rendering (Zero Janks)
   Traditional LiveView-style frameworks like this rely on JavaScript (like morphdom) on the client's main thread to parse incoming WebSocket diffs and patch the DOM. For heavy administrative interfaces, this can cause "jank" (stuttering) when large updates arrive.
   The Canvas Solution: By combining an HTML-in-Canvas API with OffscreenCanvas, you can move the entire WebSocket connection and UI rendering process into a Web Worker. The worker receives the Java server's HTML diffs, parses them, renders the HTML directly into the Offscreen Canvas, and simply pushes the pixel buffer to the main thread.
   UX Improvement: The browser's main thread remains 100% unblocked. Animations, scrolling, and user interactions stay buttery smooth regardless of how much data the Java backend pushes to the client.
2. Enhancing the Native "AI Agent" Experience
   The vadimv/server-components repository specifically highlights that AI agents natively understand the app's structure and can navigate the UI with a "Human-in-the-loop" mechanism[1].
   The Canvas Solution: When AI agents queue up actions (like filling out forms or navigating pages), traditional DOM overlays (adding borders or floating cursors) can trigger expensive browser reflows and layout thrashing.
   UX Improvement: A Canvas layer can be used to overlay the AI’s "thought process" over the HTML. You can draw 60fps bounding boxes, heatmaps, and ghostly cursors indicating exactly what the AI agent is about to do before the human approves the action, without ever mutating the actual DOM structure.
3. Latency Masking and Instant Visual Feedback
   One of the biggest UX drawbacks of pure SSR toolkits is network latency: every button click requires a round trip to the server to get the new state[1].
   The Canvas Solution: While the framework waits for the WebSocket response from the Java server, an HTML-in-Canvas approach can instantly apply localized, GPU-accelerated shaders or micro-animations exactly where the user clicked.
   UX Improvement: If a user clicks an "Increment" button, the Canvas can immediately apply a localized "ripple" effect or a subtle loading skeleton over the specific HTML element. This provides the instant tactile feedback of a Single Page Application (SPA), completely masking the 50–100ms network round-trip.
4. GPU-Accelerated View Transitions
   Because the Java backend completely dictates the state (e.g., switching from a list of Employees to a Department view), the client is normally abruptly swapping large chunks of DOM elements.
   The Canvas Solution: Since the HTML is being rendered in a Canvas, the client can easily snapshot the "before" state and the "after" state (when the new HTML diff arrives from the Java server).
   UX Improvement: You can introduce cinematic, native-feeling transitions (cross-fades, page slides, or element morphing) between server-rendered states. Users get an app-like experience without the developer writing a single line of client-side animation logic.
5. High-Performance Virtualization for Admin Panels
   Admin UIs often deal with massive datasets (e.g., tables with tens of thousands of logs or user records)[1]. Standard DOM struggles with rendering thousands of rows, requiring complex client-side JS virtualization libraries.
   The Canvas Solution: Using an HTML-in-Canvas approach, the Java backend can send a massive HTML string of data, but the Canvas renderer only draws the elements that currently intersect with the user's viewport.
   UX Improvement: Scrolling through massive server-rendered data tables becomes completely fluid. The memory footprint of the browser drops drastically because the DOM tree isn't being bloated by tens of thousands of <tr> and <td> elements.


### Summary

The client library is essentially a **sophisticated rendering and interaction engine**:
- It renders what the server tells it
- It handles browser-native gestures and animations locally
- It reports semantic interactions back to the server
- It may keep disposable rendering/gesture state
- Authoritative domain decisions happen server-side

This preserves the framework's core value: **single source of truth on the server**.
