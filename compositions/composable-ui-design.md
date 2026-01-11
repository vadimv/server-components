# Composable UI Design

This design specification describes a "second level" framework on top of the experimental web "server-components" framework for SPA-like applications with SSR: https://github.com/vadimv/server-components

## Objectives
- the design should allow build virtually any types of UI, not only CRUD-like interfaces
- the design should allow easy extension with custom components, usage patterns and scenarios
- all elements should be by design easily testable

## Implemented Features

The following are implemented in code (see source for details):
- **App, Module, Router** - Application structure with path parameter routing
- **ViewContract, ListViewContract, EditViewContract** - Contract hierarchy
- **QueryParam, PathParam** - Typed parameter definitions with defaults
- **Slot, ViewPlacement, UiRegistry** - UI component discovery and layout slots
- **AuthComponent, AuthorizationStrategy** - Authentication and authorization
- **DefaultListView, DefaultEditView** - Default UI implementations
- **Address bar sync** - Via AutoAddressBarSyncComponent
- **Custom View escape hatch** - Custom contracts + UI components via UiRegistry
- **Type-safe ContextKey** - Sealed interface with ClassKey, StringKey, DynamicKey variants
- **EditMode configuration** - SEPARATE_PAGE, QUERY_PARAM, MODAL modes per module

---

## Edit/Create Modes (Not Implemented)

The framework supports three configurable modes for create/edit workflows. Mode is configured per-module or per-contract.

### Mode Configuration

```java
public enum EditMode {
    SEPARATE_PAGE,  // Navigate to /entity/{createToken}
    QUERY_PARAM,    // Stay on list with ?create=true
    MODAL           // Pure component state, no URL change
}

// In EditViewContract - configurable create token
protected String createToken() {
    return "new";  // Override to customize: "_", "create", "0", etc.
}

protected boolean isCreateMode() {
    String id = resolveId();
    return id == null || id.equals(createToken());
}
```

### Mode 1: Separate Page (`SEPARATE_PAGE`)

Navigate to a dedicated create page with a special path token.

| URL          | Behavior                         |
|--------------|----------------------------------|
| `/posts`     | List view with "Create New" link |
| `/posts/new` | Empty edit form (create mode)    |
| `/posts/123` | Edit existing post               |

```java
// Router configuration - order matters, specific before parameterized
router.route("/posts", PostsListContract.class)
      .route("/posts/new", PostEditContract.class)    // Must come before :id
      .route("/posts/:id", PostEditContract.class);

// Contract implementation
@Override
public Post item() {
    if (isCreateMode()) {
        return null;  // Signals empty form
    }
    return postService.find(resolveId()).orElse(null);
}
```

**Pros:** Clean URLs, RESTful, bookmarkable, simple implementation
**Cons:** Full page navigation, create token is reserved

### Mode 2: Query Parameter (`QUERY_PARAM`)

Show create form based on query parameter, list remains in URL path.

| URL                  | Behavior                   |
|----------------------|----------------------------|
| `/posts`             | List view only             |
| `/posts?create=true` | List + create overlay/form |
| `/posts/123`         | Edit existing post         |

```java
// No additional route needed
// LayoutComponent checks query param to show overlay
QueryParam<Boolean> CREATE = new QueryParam<>("create", Boolean.class, false);

if (resolve(CREATE)) {
    context = context.with(ContextKeys.OVERLAY_CONTRACT, PostEditContract.class);
}
```

**Pros:** Bookmarkable, back button closes modal, list stays visible
**Cons:** URL less clean, requires LayoutComponent enhancement

### Mode 3: Pure Modal (`MODAL`)

Modal state managed entirely in components, no URL change.

| URL          | Behavior                             |
|--------------|--------------------------------------|
| `/posts`     | List view (modal state in component) |
| `/posts`     | Same URL when modal is open          |
| `/posts/123` | Edit existing post                   |

```java
// "Create New" button sends event instead of navigating
button(
    text("Create New"),
    on("click", ctx -> {
        commandsEnqueue.accept(new ComponentEventNotification("openCreateModal", null));
    })
)

// LayoutComponent handles modal state
segment.addComponentEventHandler("openCreateModal", ctx -> {
    newState.accept(state.withOverlay(createEditComponent()));
});

segment.addComponentEventHandler("closeModal", ctx -> {
    newState.accept(state.withOverlay(null));
});

segment.addComponentEventHandler("action.save", ctx -> {
    // Save, then close modal and refresh list
    contract.save(fieldValues);
    newState.accept(state.withOverlay(null));
    // Trigger list refresh via notification
});
```

**Pros:** App-like UX, simplest URL structure, no special routes
**Cons:** Not bookmarkable, refresh loses modal state, back button doesn't close modal

### Comparison

| Aspect                         | SEPARATE_PAGE | QUERY_PARAM | MODAL  |
|--------------------------------|---------------|-------------|--------|
| URL changes                    | Yes (path)    | Yes (query) | No     |
| Bookmarkable                   | Yes           | Yes         | No     |
| Back button closes             | N/A           | Yes         | No     |
| List visible during create     | No            | Yes         | Yes    |
| Implementation complexity      | Low           | Medium      | Medium |
| Requires LayoutComponent slots | No            | Yes         | Yes    |

### DefaultListView Enhancement

All modes require a "Create New" action in the list view:

```java
// In DefaultListView - mode-aware create action
private Definition renderCreateAction(EditMode mode, String modulePath) {
    return switch (mode) {
        case SEPARATE_PAGE -> a(
            attr("href", modulePath + "/" + createToken),
            text("Create New")
        );
        case QUERY_PARAM -> a(
            attr("href", "?create=true"),
            text("Create New")
        );
        case MODAL -> button(
            text("Create New"),
            on("click", ctx -> {
                commandsEnqueue.accept(new ComponentEventNotification("openCreateModal", null));
            })
        );
    };
}
```

## Notifications (Not Implemented)

Notifications allow upstream components to push updates to downstream components when external state changes.

```java
@Override
List<NotificationContract> notifications() {
    return List.of(
        // A notification is advertised in the component context so UI components can request subscription by sending an event
        new RegularNotification(
            "update", // notification name
            // somehow the caller needs to know what it wants to subscribe to e.g. a topic
            // the result of the function is a subscription handle, an Object that can be used to unsubscribe
            (topic, consumer) -> serviceA.subscribe(topic, a -> consumer.accept(convertToNotification("post-update", a))),
            handle -> serviceA.unsubscribe(handle) // invoked when all subscribed components request unsubscribe or unmounted
        )
    );
}
```

## Actions (Not Implemented)

Actions are listening for events sent by downstream components and provide a bridge between the UI and external world.

```java
@Override
List<ActionContract> actions() {
    return List.of(new RegularAction(
        "save", // action name
        value -> serviceA.save(convertToA(value))
    ));
}
```

## Layout (Not Implemented)

A concrete Layout implementation can be selected in the App or Router, e.g., StandardAdminLayout.
UI components implementations should provide a hint for the Layout how to position them.

Using Logical Slots or Areas:
- Instead of "left", use "sidebar" or "secondary"
- Instead of "center", use "main" or "primary"

This enables separation of concerns between UI and may be defined on the Module level.

### Multi-Slot Rendering

Required for `QUERY_PARAM` and `MODAL` edit modes. LayoutComponent renders multiple slots simultaneously:

```java
@Override
public ComponentView<LayoutState> componentView() {
    return _ -> state -> div(
        // Primary slot - always visible
        div(attr("class", "layout-primary"),
            state.primaryComponent()
        ),
        // Overlay slot - conditionally rendered
        state.overlayComponent() != null
            ? div(attr("class", "modal-overlay"),
                  div(attr("class", "modal-backdrop"),
                      on("click", ctx -> closeModal())
                  ),
                  div(attr("class", "modal-content"),
                      state.overlayComponent()
                  )
              )
            : of()
    );
}
```

## External World IO Lifecycle & Cleanup (Not Implemented)

The framework maintains a registry of active subscription handles associated with the user's session.
Upon session termination (WebSocket disconnect or timeout), the framework automatically invokes the unsubscribe callbacks defined in the contracts to release resources and prevent memory leaks.

## Errors Handling (Not Implemented)

- Authorization errors result with code 403 and a "non-authorized" page
- Errors or exceptions contained within modules result with code 500 and error page

## Type-Safe EventKey (Not Implemented)

Similar to ContextKey for context attributes, component events should use typed EventKey for type-safe event payload handling.

### Current Problem

Events use string names and untyped payloads:
```java
// Producer - no type safety on payload
commandsEnqueue.accept(new ComponentEventNotification("modalSaveSuccess", Map.of()));

// Consumer - must cast and hope for the best
segment.addComponentEventHandler("modalSaveSuccess", eventContext -> {
    Map<String, Object> data = (Map<String, Object>) eventContext.eventObject();  // Unsafe cast
});
```

### Proposed Solution

```java
// Define typed event keys in a registry (like ContextKeys)
public final class EventKeys {
    public static final EventKey<Map<String, Object>> MODAL_SAVE_SUCCESS =
        new EventKey<>("modalSaveSuccess", new TypeReference<Map<String, Object>>() {});

    public static final EventKey<String> OPEN_CREATE_MODAL =
        new EventKey<>("openCreateModal", String.class);  // payload is entity type

    public static final EventKey<Void> CLOSE_OVERLAY =
        new EventKey<>("closeOverlay", Void.class);  // no payload
}

// Producer - type-checked at compile time
commandsEnqueue.accept(EventKeys.MODAL_SAVE_SUCCESS.emit(fieldValues));

// Consumer - type-safe, no cast needed
subscriber.addEventHandler(EventKeys.MODAL_SAVE_SUCCESS, (payload) -> {
    Map<String, Object> data = payload;  // Already typed!
});

// Wildcard support (like ContextKey.DynamicKey)
public static final EventKey.Dynamic<String> STATE_UPDATED =
    new EventKey.Dynamic<>("stateUpdated", String.class);

// Subscribe to stateUpdated.* events
subscriber.addEventHandler(EventKeys.STATE_UPDATED.prefix(), (name, value) -> {
    String paramName = name.substring("stateUpdated.".length());
    // handle dynamic event
});
```

### Benefits
- Compile-time type checking for event payloads
- IDE autocomplete for event names
- Refactoring support (rename event → all usages updated)
- No runtime ClassCastException surprises
- Consistent with ContextKey pattern

---

## Schema DSL (Not Implemented)

ListSchema should be defined via DSL rather than auto-derived from records. Auto-derivation is useful for prototyping but production needs explicit field configuration.

### Current Auto-Derivation (Prototyping Only)

```java
// Works but lacks customization
ListSchema schema = ListSchema.fromFirstItem(posts.get(0));
```

### Proposed Schema DSL

```java
public ListSchema schema() {
    return ListSchema.builder()
        .field("id", FieldType.ID)
            .hidden()  // Not shown in list/form
        .field("title", FieldType.STRING)
            .label("Post Title")
            .required()
            .maxLength(200)
            .placeholder("Enter title...")
        .field("content", FieldType.TEXT)
            .label("Content")
            .widget(Widget.RICH_TEXT)  // Hint for UI component
        .field("status", FieldType.ENUM)
            .options("DRAFT", "PUBLISHED", "ARCHIVED")
            .defaultValue("DRAFT")
        .field("createdAt", FieldType.DATETIME)
            .label("Created")
            .readOnly()
            .format("yyyy-MM-dd HH:mm")
        .field("author", FieldType.REFERENCE)
            .referenceTo(User.class)
            .displayField("name")
        .build();
}
```

### Validation Rules

```java
.field("email", FieldType.STRING)
    .validate(Validators.email())
    .validate(Validators.unique())  // Custom validator

.field("age", FieldType.INTEGER)
    .validate(Validators.range(0, 150))

.field("password", FieldType.STRING)
    .validate(Validators.minLength(8))
    .validate(Validators.pattern("[A-Za-z0-9]+"))
    .widget(Widget.PASSWORD)
```

### Widget Hints

```java
public enum Widget {
    TEXT,           // Default text input
    TEXTAREA,       // Multi-line text
    RICH_TEXT,      // WYSIWYG editor
    PASSWORD,       // Masked input
    SELECT,         // Dropdown
    RADIO,          // Radio buttons
    CHECKBOX,       // Checkbox
    DATE_PICKER,    // Date input
    FILE_UPLOAD,    // File input
    AUTOCOMPLETE,   // Search with suggestions
    HIDDEN          // Not rendered
}
```

---

## List Route Resolution (Not Implemented - Future Enhancement)

Currently `EditViewContract.listRoute()` uses convention-based path derivation with query param restoration. This should be enhanced with:

### Option 1: Referer Header

```java
public String listRoute() {
    // Try HTTP Referer first
    String referer = context.get(ContextKeys.HTTP_REFERER);
    if (referer != null && isValidListRoute(referer)) {
        return referer;
    }
    // Fall back to convention
    return deriveFromRoutePattern();
}
```

### Option 2: Session-Based Navigation Stack

```java
// Framework maintains navigation history in session
NavigationStack navStack = context.get(NavigationStack.class);
String previousRoute = navStack.peek();

// Or explicit breadcrumb
navStack.push("/posts?p=3&sort=desc");  // When entering edit
navStack.pop();  // When leaving edit
```

### Option 3: Explicit Contract Method

```java
// Contract explicitly declares its list route
public abstract class EditViewContract<T> {
    // Override for custom navigation
    public String listRoute() {
        return "/posts";  // Explicit, no magic
    }
}
```

### Recommended Approach

Combine all three with priority:
1. **Explicit override** - If contract overrides `listRoute()`, use it
2. **Session navigation stack** - If available, use previous route
3. **Referer header** - If valid and same-origin
4. **Convention fallback** - Derive from route pattern

---

## Testing (Partial)

- design should allow testing all elements in isolation
- Property-Based-Testing (PBT) is the method to test for the UI components implementations

## Agentic Interface (Not Implemented)

The system supports a CommandModule that accepts natural language. The Framework exposes a Reflective Schema (listing available Routes, Modules, and Contracts) to an AI Agent.

The Agent is authorized to emit System Events that:
- Mutate Shared Session State (e.g., Theme, Filters)
- Trigger Navigation
- Inject Layout Overrides (changing Slot assignments dynamically)

### Semantic Annotations

Add descriptions so the AI knows what the UI does:
```java
@AiDescription("Displays a list of items with sort/filter capabilities")
// or better have a separate method to implement for that e.g. String description()
public class ListViewContract { ... }
```

### Registry as a Catalog

Expose an API to dump the Registry as a JSON Schema. This serves as the "Prompt Context" for the LLM.

### Streaming Router

Since AI is slow, the Router needs to support Partial Rendering. It should be able to render the "Shell" of the page while the AI is still "thinking" about the optimal layout for the inner content.

```java
public class CommandModule extends Module {

    // 1. View: The Input Box
    @Override
    List<ViewPlacement> views() {
        return List.of(
            new ViewPlacement(Slot.OVERLAY, new PromptContract())
        );
    }

    // 2. Action: Receive the User's Intent
    @Override
    List<ActionContract> actions() {
        return List.of(new RegularAction("submit-prompt", this::handlePrompt));
    }

    // 3. The "Brain"
    private void handlePrompt(ActionContext ctx, String userText) {
        // Step A: Gather Context (Where is the user? What can they do?)
        var appSchema = registry.generateSchema(); // "I have List, Details, Dashboard..."
        var currentRoute = ctx.getCurrentRoute();

        // Step B: Ask AI (The Translation Layer)
        AiInstruction instruction = aiService.interpret(userText, appSchema, currentRoute);

        // Step C: Execute Framework Command
        executeInstruction(ctx, instruction);
    }
}
```
