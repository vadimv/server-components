# AI Agent Integration — Design Spec

## Overview

A possible scenario is when a user types commands for:
- navigation within a page
- interacting with a contract
- discovering and adding new contracts plus services

An AI agent sits between a user (via a prompt/chat interface) and the framework,
translating natural-language intent into framework operations:

```
User prompt --> Agent --> AgentIntent --> IntentGate (rules) --> Event --> UI
                 ^                           |
                 +--- block / confirm -------+
```

The integration builds on two existing concepts:

1. **Contracts with Views** — UI units grouped in Compositions (unchanged)
2. **Services** — everything else: logic, lifecycle hooks, plugins, AI agents


## Architecture: Contracts vs Services

### Contracts (unchanged)

Contracts own UI. They are scene-scoped, always have views, and are grouped in Compositions.
A contract's job is:
- Read services from `Lookup`
- Bridge service data into view context (`enrichContext`)
- Bridge user events into service calls (`registerHandlers`)

### Services

Services own everything else. A service is any object registered in the `Services` map.
The framework places them in context; contracts and components retrieve them via `Lookup`.

Services are **not** required to extend any base class or implement any interface.
They _may_ depend on framework types — this is already the established pattern:

- `AuthProvider` takes `ComponentContext`, returns `Definition`, calls `CommandsEnqueue`
- `AuthorizationStrategy` takes `ViewContract` and `Lookup`

A service that needs event access receives `Lookup` from its caller (a contract or component),
not from the framework directly.

### Registration levels

| Level           | Registration                                           | Scope                     | Available to                      |
|-----------------|--------------------------------------------------------|---------------------------|-----------------------------------|
| **App**         | `Services.service(Class, Object)`                      | Singleton, entire app     | All compositions, all sessions    |
| **Composition** | `new Composition(router, layout, groups, services)`    | Per-scene, feature-domain | Contracts within that composition |


## Optional Lifecycle Interface

Services that need lifecycle hooks can implement a marker interface:

```java
interface LifecycleHandler {
    default void onStart(Lookup lookup) {}
    default void onStop() {}
}
```

The framework checks at the appropriate scope boundary:

```java
for (Object service : services.values()) {
    if (service instanceof LifecycleHandler handler) {
        handler.onStart(lookup);
    }
}
```

- App-level: `onStart()` at app startup, `onStop()` at shutdown
- Composition-level: `onStart()` when a scene using that composition is built,
  `onStop()` on navigation away

Services that don't implement `LifecycleHandler` are unaffected — the `instanceof` check
is a no-op. This is strictly additive.


## Contract Discovery

### Two levels (contract scope)

Discovery at the contract level uses two complementary methods.
For higher-level discovery (app structure, framework capabilities), see
[Layered Discovery Model](#layered-discovery-model).

**a) Navigation discovery** — "What contracts exist?"
Uses the `StructureNode` tree extracted from `Group.structureTree()`. Each node carries
a `label` (short name), an optional `description` (natural-language purpose for AI agents),
child nodes, and bound contract classes. The tree gives the agent hierarchical context —
e.g., "Posts" and "Comments" live under "Admin" — enabling coarse-to-fine intent matching.
`StructureNode.agentDescription()` renders the tree as a human-readable summary.

**b) Contract capability discovery** — "What can this contract do?"
Every contract exposes two methods for agent discovery:

| Method              | Returns                        | Purpose                                        |
|---------------------|--------------------------------|-------------------------------------------------|
| `agentDescription()`| Natural-language string        | Live state — "what am I looking at right now?"  |
| `agentActions()`    | `List<AgentAction>`            | Declared actions — "what can the agent do here?"|

The type hierarchy provides defaults; concrete contracts can override either method
to add domain context, enrich state details, or restrict/extend the available actions.


### `agentDescription()` — live self-description

Each contract provides a natural-language description of what it does AND its current state,
including visible data. The agent uses this to reason about items (match by name, filter,
compute updates).

```java
// ViewContract — sensible default
public abstract class ViewContract {
    public String agentDescription() {
        return title();
    }
}

// ListViewContract — live state including visible items
public abstract class ListViewContract<T> extends ViewContract {
    @Override
    public String agentDescription() {
        String itemsSummary = items().stream()
            .map(Object::toString)
            .collect(Collectors.joining("\n  "));
        return "Displays a list of " + title() + ".\n"
             + "Current page: " + page() + ", sort: " + sort() + "\n"
             + "Items on page: " + items().size() + ", page size: " + pageSize() + "\n"
             + "Items:\n  " + itemsSummary + "\n"
             + "Schema: " + schema().fields().stream()
                  .map(f -> f.name() + ":" + f.fieldType())
                  .collect(Collectors.joining(", "));
    }
}

// EditViewContract — entity + schema + live state
public abstract class EditViewContract<T> extends ViewContract {
    @Override
    public String agentDescription() {
        Object entity = item();
        String fields = schema().fields().stream()
                .map(f -> f.name() + ":" + f.fieldType())
                .collect(Collectors.joining(", "));
        return "Edit form for " + title() + ".\n"
             + "Entity: " + (entity != null ? entity.toString() : "none") + "\n"
             + "Fields: " + fields;
    }
}

// PostsListContract — domain specifics
public class PostsListContract extends ListViewContract<Post> {
    @Override
    public String agentDescription() {
        return super.agentDescription() + "\n"
             + "Domain: blog posts (id, title, content).";
    }
}
```

Example output for the agent:

```
Displays a list of Posts.
Current page: 1, sort: asc
Items on page: 3, page size: 10
Items:
  Post[id=1, title=Post Title 1, content=Hello]
  Post[id=2, title=Post Title 2, content=World]
  Post[id=3, title=Post Title 3, content=Test]
Schema: id:ID, title:STRING, content:TEXT, published:BOOLEAN
Domain: blog posts (id, title, content).
```


### `agentActions()` — declared action vocabulary

Each contract declares the actions an agent can invoke. An action binds a name
to an `EventKey`, with a human-readable description and payload schema. This is
the **single source of truth** for what the agent is allowed to do on a given contract.

```java
// Action descriptor — pure data, declared by contracts
record AgentAction(
    String action,                 // action name used in AgentIntent
    EventKey<?> eventKey,          // the framework event to publish
    String description,            // human-readable purpose
    String payloadDescription      // payload schema (null for VoidKey)
) {}
```

Base classes provide sensible defaults; concrete contracts override to add, remove,
or customize actions:

```java
// ListViewContract — standard list actions
public abstract class ListViewContract<T> extends ViewContract {
    @Override
    public List<AgentAction> agentActions() {
        return List.of(
            new AgentAction("create", CREATE_ELEMENT_REQUESTED,
                "Open create form for a new item", null),
            new AgentAction("edit", EDIT_ELEMENT_REQUESTED,
                "Open edit form for an item", "String: row ID"),
            new AgentAction("delete", BULK_DELETE_REQUESTED,
                "Delete items by their IDs", "Set<String>: row IDs"),
            new AgentAction("page", PAGE_CHANGE_REQUESTED,
                "Navigate to a page number", "Integer: page number (1-based)"),
            new AgentAction("select_all", SELECT_ALL_REQUESTED,
                "Select all rows on the current page", null)
        );
    }
}

// FormViewContract — form actions (inherited by Edit and Create)
public abstract class FormViewContract<T> extends ViewContract {
    @Override
    public List<AgentAction> agentActions() {
        String fieldNames = schema().fields().stream()
            .map(f -> f.name() + ":" + f.fieldType())
            .collect(Collectors.joining(", "));
        return List.of(
            new AgentAction("save", FORM_SUBMITTED,
                "Submit form data",
                "Map<String, Object>: {" + fieldNames + "}"),
            new AgentAction("cancel", CANCEL_REQUESTED,
                "Cancel and go back", null)
        );
    }
}

// EditViewContract — adds delete to form actions
public abstract class EditViewContract<T> extends ViewContract {
    @Override
    public List<AgentAction> agentActions() {
        List<AgentAction> actions = new ArrayList<>(super.agentActions());
        actions.add(new AgentAction("delete", DELETE_REQUESTED,
            "Delete the current entity", null));
        return List.copyOf(actions);
    }
}

// Read-only list — concrete contract removes mutating actions
public class AuditLogListContract extends ListViewContract<AuditEntry> {
    @Override
    public List<AgentAction> agentActions() {
        return super.agentActions().stream()
            .filter(a -> List.of("page", "select_all").contains(a.action()))
            .toList();
    }
}
```

Example output for the agent (from `ContractProfile`):

```
Actions:
  create  [VoidKey]          — Open create form for a new item
  edit    [SimpleKey<String>] — Open edit form for an item (payload: row ID)
  delete  [SimpleKey<Set>]   — Delete items by their IDs (payload: row IDs)
  page    [SimpleKey<Integer>]— Navigate to a page number (payload: 1-based)
  select_all [VoidKey]       — Select all rows on the current page
```


### `ContractProfile` — combined view for the agent

`ContractProfile` merges `agentDescription()` (live state) with `agentActions()`
(action vocabulary) into a single object the agent consumes:

```java
record ContractProfile(String description,
                        List<AgentAction> actions,
                        Class<?> contractClass) {

    static ContractProfile of(ViewContract contract) {
        if (contract == null) {
            return new ContractProfile(null, List.of(), Void.class);
        }

        String description = contract instanceof AgentInfo info
                ? info.agentDescription()
                : null;

        List<AgentAction> actions = contract.agentActions();

        return new ContractProfile(description, actions, contract.getClass());
    }

    boolean isList() { return ListViewContract.class.isAssignableFrom(contractClass); }
    boolean isEdit() { return EditViewContract.class.isAssignableFrom(contractClass); }
    boolean isForm() { return FormViewContract.class.isAssignableFrom(contractClass); }
}
```

The agent receives the description for reasoning and the actions for intent construction.
No reflection needed — the contract is the single source of truth.


## Intent Gate — Rule Engine

**The agent never publishes events directly.** It emits an intent (pure data), and
a rule engine decides what happens. This ensures all agent actions are auditable,
blockable, and confirmable.

### Flow

```
User: "delete post 5"
  --> Agent parses intent
  --> AgentIntent(action="delete", params={id:"5"}, target=PostEditContract)
  --> IntentGate evaluates rules
      --> Allow?   --> IntentDispatcher publishes DELETE_REQUESTED
      --> Block?   --> Reply "Not permitted: requires admin role"
      --> Confirm? --> Reply "Delete post 5? Type 'yes' to confirm"
                       User: "yes"
                       --> IntentDispatcher publishes DELETE_REQUESTED
```

### Core types

```java
// The agent's output — pure data, never an action
record AgentIntent(String action,
                   Map<String, Object> params,
                   Class<? extends ViewContract> targetContract) {}

// The gate between intent and execution
interface IntentGate {
    GateResult evaluate(AgentIntent intent, Lookup lookup);
}

// Gate decision
sealed interface GateResult {
    record Allow(AgentIntent intent) implements GateResult {}
    record Block(String reason) implements GateResult {}
    record Confirm(String question, AgentIntent intent) implements GateResult {}
}
```

### Intent dispatcher — the only thing with publish access

The dispatcher is **generic** — it looks up the action in the contract's declared
`agentActions()` and publishes the associated `EventKey`. No hardcoded switch for
contract-specific events; the contract is the single source of truth.

```java
class IntentDispatcher {
    void dispatch(AgentIntent intent, ViewContract contract, Lookup lookup, IntentGate gate) {
        GateResult result = gate.evaluate(intent, lookup);
        switch (result) {
            case GateResult.Allow a -> publishEvent(a.intent(), contract, lookup);
            case GateResult.Block b -> replyToAgent(b.reason());
            case GateResult.Confirm c -> askConfirmation(c.question(), c.intent());
        }
    }

    @SuppressWarnings("unchecked")
    private void publishEvent(AgentIntent intent, ViewContract contract, Lookup lookup) {
        // Navigation is contract-independent — handled directly
        if ("navigate".equals(intent.action())) {
            lookup.publish(SET_PRIMARY, intent.targetContract());
            return;
        }

        // All other actions: look up in the contract's declared actions
        contract.agentActions().stream()
            .filter(a -> a.action().equals(intent.action()))
            .findFirst()
            .ifPresentOrElse(
                action -> {
                    EventKey<?> key = action.eventKey();
                    if (key instanceof EventKey.VoidKey vk) {
                        lookup.publish(vk);
                    } else if (key instanceof EventKey.SimpleKey<?> sk) {
                        lookup.publish((EventKey.SimpleKey) sk, intent.params().get("payload"));
                    }
                },
                () -> replyToAgent("Unknown action: " + intent.action())
            );
    }
}
```

Because the dispatcher reads from `agentActions()`, adding a new action to a contract
automatically makes it available to the agent — no dispatcher changes needed.

### Separation of concerns

- **Agent** — produces `AgentIntent` from prompts. No `Lookup`, no publish access.
  Testable in isolation.
- **IntentGate** — evaluates rules against intents. No publish access.
  Testable in isolation.
- **IntentDispatcher** — the only thing that calls `lookup.publish()`.
  Controlled, auditable, single point of event emission.

### Example rules

```java
// Role-based
class AdminOnlyDeleteRule implements IntentGate {
    public GateResult evaluate(AgentIntent intent, Lookup lookup) {
        if ("delete".equals(intent.action())) {
            String[] roles = lookup.get(ContextKeys.AUTH_ROLES);
            if (!Arrays.asList(roles).contains("admin")) {
                return new GateResult.Block("Delete requires admin role");
            }
        }
        return new GateResult.Allow(intent);
    }
}

// Confirmation for destructive actions
class ConfirmDestructiveRule implements IntentGate {
    public GateResult evaluate(AgentIntent intent, Lookup lookup) {
        if ("delete".equals(intent.action())) {
            return new GateResult.Confirm(
                "Delete " + intent.params().get("id") + "? Type 'yes' to confirm.",
                intent);
        }
        return new GateResult.Allow(intent);
    }
}

// Composite gate
class CompositeGate implements IntentGate {
    private final List<IntentGate> rules;

    public GateResult evaluate(AgentIntent intent, Lookup lookup) {
        for (IntentGate rule : rules) {
            GateResult result = rule.evaluate(intent, lookup);
            if (result instanceof GateResult.Block || result instanceof GateResult.Confirm) {
                return result;  // First block or confirm wins
            }
        }
        return new GateResult.Allow(intent);
    }
}
```


## AI Agent as a Service

### Agent service (App-level, singleton)

The agent produces intents, never events. It receives the active contract's description
and builds an intent from the user's prompt.

```java
class AgentService {
    // Produces intent, NOT events — no Lookup dependency
    AgentIntent handlePrompt(String prompt, ContractProfile activeContract,
                             StructureNode structureTree) {
        // Parse command against active contract's description and capabilities
        // Match navigation targets using group labels and descriptions
        // Return intent for the rule engine to evaluate
    }
}
```

### Agent chat contract (scene-scoped, UI shell)

```java
class AgentChatContract extends ViewContract {
    private final AgentService agent;
    private final IntentDispatcher dispatcher;
    private final IntentGate gate;
    private final StructureNode structureTree;

    AgentChatContract(Lookup lookup, StructureNode structureTree) {
        super(lookup);
        this.agent = lookup.getRequired(AgentService.class);
        this.dispatcher = lookup.getRequired(IntentDispatcher.class);
        this.gate = lookup.getRequired(IntentGate.class);
        this.structureTree = structureTree;
    }

    @Override
    protected void registerHandlers() {
        subscribe(SEND_PROMPT, (name, prompt) -> {
            Scene scene = lookup.get(ContextKeys.SCENE);
            ViewContract activeContract = scene.routedContract();
            ContractProfile profile = ContractProfile.of(activeContract);

            AgentIntent intent = agent.handlePrompt(prompt, profile, structureTree);
            dispatcher.dispatch(intent, activeContract, lookup, gate);
        });
    }

    @Override
    public ComponentContext enrichContext(ComponentContext context) {
        return context;
    }

    @Override
    public String title() { return "Assistant"; }
}
```

### Wiring

```java
// Groups with descriptions for agent discovery
Group mainContracts = new Group("Admin")
    .description("Administrative tools for managing blog content")
    .add(new Group("Posts")
        .description("CRUD operations for blog posts: create, edit, list, delete")
        .bind(PostsListContract.class, ctx -> new PostsListContract(ctx, postService), DefaultListView::new)
        .bind(PostEditContract.class, ctx -> new PostEditContract(ctx, postService), DefaultEditView::new))
    .add(new Group("Comments")
        .description("Moderation and management of user comments")
        .bind(CommentsListContract.class, ctx -> new CommentsListContract(ctx, commentService), DefaultListView::new));

Group systemContracts = new Group()
    .bind(AgentChatContract.class,
          ctx -> new AgentChatContract(ctx, mainContracts.structureTree()), AgentChatView::new);

// Services
IntentGate gate = new CompositeGate(List.of(
    new AdminOnlyDeleteRule(),
    new ConfirmDestructiveRule()
));

new App(config, compositions,
    new Services()
        .service(AgentService.class, new AgentService())
        .service(IntentDispatcher.class, new IntentDispatcher())
        .service(IntentGate.class, gate));
```


## Agent Interaction Scenarios

### 1. Navigation — "Show posts"

```
Agent: parse "show posts" against StructureNode tree
  --> match "posts" to Group(label="Posts", description="CRUD operations for blog posts")
  --> resolve PostsListContract from that group's contracts
  --> AgentIntent(action="navigate", target=PostsListContract)
  --> IntentGate: Allow
  --> publish SET_PRIMARY(PostsListContract.class)
```

### 2. Pagination — "Go to page 3"

```
Agent: parse "page 3" against active contract description
  --> active contract agentDescription() says "Current page: 1..."
  --> ListViewContract supports PAGE_CHANGE_REQUESTED
  --> AgentIntent(action="page", params={page: 3})
  --> IntentGate: Allow
  --> publish PAGE_CHANGE_REQUESTED(3)
```

### 3. Selection — "Select all comments on the page"

```
Agent: parse "select all" against active contract
  --> ListViewContract supports selection
  --> AgentIntent(action="select_all")
  --> IntentGate: Allow
  --> publish SELECT_ALL_REQUESTED
```

### 4. Open editor — "Open editor for selected"

```
Agent: active contract is ListViewContract, has selection state
  --> AgentIntent(action="edit", params={id: firstSelectedId})
  --> IntentGate: Allow
  --> publish EDIT_ELEMENT_REQUESTED(id)
```

### 5. Delete — "Delete post 1"

```
Agent: AgentIntent(action="delete", params={id: "1"})
  --> IntentGate: ConfirmDestructiveRule triggers
  --> Reply: "Delete post 1? Type 'yes' to confirm."
  User: "yes"
  --> IntentGate: Allow (confirmation received)
  --> publish BULK_DELETE_REQUESTED(Set.of("1"))
```

### 6. Create — "Create post about cats"

```
Agent: AgentIntent(action="create", params={title: "About cats"})
  --> IntentGate: Allow
  --> publish CREATE_ELEMENT_REQUESTED
  --> (future: pre-fill form via SHOW payload data)
```


### 7. Delete by name — "Delete 'Post Title 1'"

The agent uses `agentDescription()` to see the visible items, matches by name,
resolves the ID, and emits a delete intent with the resolved ID.

```
Agent reads agentDescription():
  "Items:
    Post[id=1, title=Post Title 1, content=Hello]
    Post[id=2, title=Post Title 2, content=World]
  Schema: id:ID, title:STRING, content:TEXT"

Agent: match "Post Title 1" against items → id "1"
Agent reads agentActions():
  delete [SimpleKey<Set>] — "Delete items by their IDs" (payload: row IDs)

AgentIntent(action="delete", params={payload: Set.of("1")})
  --> IntentGate: ConfirmDestructiveRule triggers
  --> Reply: "Delete 'Post Title 1' (id: 1)? Type 'yes' to confirm."
  User: "yes"
  --> IntentDispatcher looks up "delete" in agentActions()
  --> finds BULK_DELETE_REQUESTED
  --> publish BULK_DELETE_REQUESTED(Set.of("1"))
```

Key: the agent resolves "Post Title 1" → id "1" using visible item data from
`agentDescription()`. The framework event uses IDs, not names.


### 8. Search/filter — "Search all posts with id < 2"

The agent reads visible items from `agentDescription()` and filters client-side.
No framework event needed — the agent acts as a read-only query tool over the
current page data.

```
Agent reads agentDescription():
  "Items:
    Post[id=1, title=Post Title 1, content=Hello]
    Post[id=2, title=Post Title 2, content=World]
    Post[id=3, title=Post Title 3, content=Test]
  Schema: id:ID, title:STRING, content:TEXT"

Agent: filter items where id < 2
Agent replies: "Found 1 post matching 'id < 2':
  - Post Title 1 (id: 1)"
```

For multi-page search, the agent can paginate and accumulate results:

```
Agent: AgentIntent(action="page", params={payload: 2})
  --> navigate to page 2
  --> read agentDescription() for page 2 items
  --> accumulate matching results across pages
  --> reply with combined results
```

Note: this is agent-side reasoning, not a framework feature. A future optimization
could add a `SEARCH_REQUESTED` event for server-side filtering, but the agent
can handle common cases with the data already visible in `agentDescription()`.


### 9. Update field — "Update post 2 adding 'test'"

A two-step interaction: the agent opens the edit form, reads the current entity,
computes the update, and submits. Both steps use declared `agentActions()`.

```
Step 1 — Open editor:
Agent reads agentActions() on ListViewContract:
  edit [SimpleKey<String>] — "Open edit form for an item" (payload: row ID)
AgentIntent(action="edit", params={payload: "2"})
  --> IntentDispatcher → EDIT_ELEMENT_REQUESTED("2")
  --> EditViewContract opens with post 2

Step 2 — Read, compute, save:
Agent reads agentDescription() on EditViewContract:
  "Edit form for Posts.
   Entity: Post[id=2, title=Post Title 2, content=World]
   Fields: id:ID, title:STRING, content:TEXT, published:BOOLEAN"

Agent reads agentActions() on EditViewContract:
  save [SimpleKey<Map>] — "Submit form data"
       (payload: {id:ID, title:STRING, content:TEXT, published:BOOLEAN})
  delete [VoidKey] — "Delete the current entity"
  cancel [VoidKey] — "Cancel and go back"

Agent: parse "adding 'test'" → append " test" to content field
Agent: compute payload = {id: "2", title: "Post Title 2",
                          content: "World test", published: true}
AgentIntent(action="save", params={payload: {id: "2", title: "Post Title 2",
                                             content: "World test", published: true}})
  --> IntentGate: Allow
  --> IntentDispatcher looks up "save" in agentActions()
  --> finds FORM_SUBMITTED
  --> publish FORM_SUBMITTED(Map.of(...))
  --> EditViewContract validates and saves
  --> ACTION_SUCCESS → navigate back to list
```

Key: the agent uses `agentDescription()` to read the current entity, applies the
user's modification, then uses `agentActions()` to find the `save` action and its
payload schema. The form's validation still runs — the agent doesn't bypass it.


## Framework Changes Required

### Minimal (for prototype)

1. **`agentDescription()`** — default method on `ViewContract`, overridden in
   `ListViewContract` (with visible items + schema), `EditViewContract`,
   `CreateViewContract` with live state
2. **`agentActions()`** — default method on `ViewContract` (returns empty list),
   overridden in `ListViewContract`, `FormViewContract`, `EditViewContract` with
   declared `AgentAction` entries. Concrete contracts can override to add/remove actions
3. **`AgentAction` record** — `(String action, EventKey<?> eventKey, String description,
   String payloadDescription)` — the action descriptor
4. **`SELECT_ALL_REQUESTED`** — new event in `ListViewContract`, handled by `DefaultListView`
5. **`SELECTION_CHANGED`** — new event published by `DefaultListView` on selection changes,
   so the agent can track current selection

### Structural (for full integration)

6. **Composition-level services** — add `Services` field to `Composition`, inject into
   context during scene building
7. **`LifecycleHandler` interface** — opt-in lifecycle hooks via `instanceof`

### Already exists (zero changes)

- Prompt UI (`PromptContract` + `PromptView` + `PromptService`)
- Navigation events (`SET_PRIMARY`)
- Pagination events (`PAGE_CHANGE_REQUESTED`)
- CRUD events (`EDIT_ELEMENT_REQUESTED`, `CREATE_ELEMENT_REQUESTED`,
  `BULK_DELETE_REQUESTED`, `DELETE_REQUESTED`)
- Form events (`FORM_SUBMITTED`, `CANCEL_REQUESTED`, `DELETE_REQUESTED`)
- Navigation structure (`Group` with labels and descriptions, `StructureNode` metadata tree)
- Contract type hierarchy as capability system (`ListViewContract`, `EditViewContract`, etc.)


## Layered Discovery Model

### Uniform shape: description + actions at every level

The same two-method pattern (`agentDescription()` + discoverable actions) applies
at every level of the hierarchy. Each level provides progressively more specific
context:

```
Framework   →  "This is a compositions-based app. List views support pagination,
                selection, CRUD. Edit views support save, cancel, delete.
                Sign-out is available."

App         →  "Admin panel with Posts and Comments sections."

Composition →  "Posts domain: list at /posts, edit at /posts/:id.
                Comments domain: list at /comments."

Contract    →  "Displaying 3 posts, page 1 of 3. Sort: asc.
                Actions: create, edit, delete, page, select_all."
```

| Level           | Description source                       | Actions source                      |
|-----------------|------------------------------------------|--------------------------------------|
| **Framework**   | Built-in: contract type capabilities     | Framework-level actions (sign-out)   |
| **App**         | `StructureNode.agentDescription()`       | App-level services                   |
| **Composition** | `Composition` metadata + router          | Composition-scoped services          |
| **Contract**    | `agentDescription()` (live state)        | `agentActions()` (declared actions)  |


### Framework-level context

The framework itself is discoverable. An agent should understand what contract
types exist and what they generally offer, without inspecting a specific instance:

```
Contract types:
  List view — paginated data list. Supports: create, edit, delete, page, select_all.
              Shows items with schema. Selection state tracked.
  Edit view — form for editing an existing entity. Supports: save, cancel, delete.
              Shows current entity fields.
  Create view — form for creating a new entity. Supports: save, cancel.
              Shows empty fields with schema.

Framework actions:
  sign-out — End the current session (when auth provider supports it)
```

Framework-level actions like sign-out are not tied to any contract. They exist
at the `AuthProvider` / `App` level and need a separate discovery mechanism.


### App-level context

`StructureNode.agentDescription()` renders the Group hierarchy with labels and
descriptions. This gives the agent an overview of all available sections:

```
Admin — Administration panel
  Posts — Blog posts with create, edit, delete, and search
  Comments — User comments with moderation
```

This is already implemented: `Group.description(String)` flows into `StructureNode`,
and `StructureNode.agentDescription()` renders the tree.


### Agent scoping

One agent per application. The agent's scope determines what it can see and do.
Scope levels are additive — a wider scope includes all narrower levels:

```
Contract scope  →  Sees: active contract only
                   Can: contract-level actions (create, edit, delete, page...)

App scope       →  Sees: structure tree + active contract
                   Can: navigate between sections + contract actions

Framework scope →  Sees: all levels + framework capabilities
                   Can: sign-out + navigate + contract actions
```

The agent's scope is configured at setup time. Its `AgentContext` is populated
with the layers matching that scope, and `AgentActionFilter` restricts which
actions are visible. For example, a read-only agent at app scope would see
the structure tree and contract descriptions, but only non-mutating actions
(page, select_all).


### Action visibility filters

The `IntentGate` already controls what an agent can *execute*, but scoping also
controls what an agent can *discover*. An agent scoped to read-only operations
should not even see delete actions — they are filtered before reaching the agent,
not just blocked at execution time.

```java
// Action filter — controls what the agent discovers
interface AgentActionFilter {
    List<AgentAction> filter(List<AgentAction> actions, Lookup context);
}

// Read-only filter
class ReadOnlyFilter implements AgentActionFilter {
    private static final Set<String> ALLOWED = Set.of("page", "select_all");

    public List<AgentAction> filter(List<AgentAction> actions, Lookup context) {
        return actions.stream()
            .filter(a -> ALLOWED.contains(a.action()))
            .toList();
    }
}

// Role-based filter
class RoleBasedFilter implements AgentActionFilter {
    public List<AgentAction> filter(List<AgentAction> actions, Lookup context) {
        String[] roles = context.get(ContextKeys.AUTH_ROLES);
        if (roles == null || !Arrays.asList(roles).contains("admin")) {
            return actions.stream()
                .filter(a -> !"delete".equals(a.action()))
                .toList();
        }
        return actions;
    }
}
```

This is the **discovery-time complement** to `IntentGate` (execution-time):
- `AgentActionFilter` — "what actions does this agent see?" (before prompt parsing)
- `IntentGate` — "is this specific intent allowed?" (after prompt parsing)


### `AgentContext` — runtime materialisation of scope

`AgentContext` is the scope-aware bundle that provides description + actions for
each layer, with filtering applied. It is not a convenience wrapper — it is the
runtime representation of agent scope. Without it, every agent re-implements the
same layer assembly and filtering logic.

```java
// AgentContext assembles the right layers for an agent's scope
AgentContext ctx = AgentContext.forScope(scope, lookup);

// Each layer: description + actions (uniform shape)
ctx.frameworkDescription();    // "List views support pagination, CRUD..."
ctx.frameworkActions();        // [sign-out]

ctx.appDescription();          // "Admin panel with Posts and Comments"
ctx.appActions();              // (extensible — app-level services)

ctx.contractDescription();     // "Displaying 3 posts, page 1..."
ctx.contractActions();         // [create, edit, delete, page, select_all]
```

Scope determines which layers are populated. A contract-scoped agent receives
only `contractDescription()` and `contractActions()`. A system-scoped agent
receives all layers. `AgentActionFilter` is applied inside `AgentContext` —
the agent receives pre-filtered actions, never seeing what its scope excludes.

Agents act on behalf of the logged-in user and should operate within a subset
of the user's entitlements. The `AgentActionFilter` + `IntentGate` combination
enforces this: filters restrict discovery, gates restrict execution.


### Open questions

1. **How should framework-level actions be declared?** Deferred — to be
   revisited when framework-level actions beyond sign-out are needed.

2. **Should composition-level context include route information?** Deferred —
   parameterized routes may be too low-level for natural-language agents.

3. **One agent per application** — for now, only a single agent is allowed per
   application. Multi-agent coordination (shared state, delegation, registries)
   is deferred until the single-agent model is proven and stable.


## Design Principles

- **Contracts own UI, services own logic** — the agent service does reasoning;
  the chat contract renders the conversation
- **Agent never publishes directly** — it emits `AgentIntent`; the `IntentGate` decides;
  the `IntentDispatcher` publishes
- **Two methods per contract** — `agentDescription()` provides live state (read-only
  context: "what am I looking at?"); `agentActions()` declares the action vocabulary
  ("what can the agent do here?"). Together they are the complete agent-facing API
- **Uniform discovery at every level** — the same description + actions pattern applies
  at framework, app, composition, and contract levels. Each level provides progressively
  more specific context
- **Contract is the single source of truth** — the `IntentDispatcher` reads actions from
  `agentActions()`, not from a hardcoded switch. Adding an action to a contract
  automatically makes it available. Removing an action hides it from the agent
- **One agent, scoped** — one agent per application, operating at a configured scope
  level. Action filters control discovery; intent gates control execution.
  The agent acts on behalf of the logged-in user within a subset of their entitlements
- **`AgentContext` is the scope** — the runtime bundle that assembles description +
  actions for each layer with filtering applied. Agents receive pre-filtered context,
  not raw sources
- **Rules are composable** — `IntentGate` implementations chain; first block/confirm wins
- **Testable in isolation** — agent, gate, and dispatcher have no shared mutable state;
  each testable independently
- **Services are plain objects** — no mandatory base class; framework types are
  dependencies, not inheritance
- **Opt-in lifecycle** — `instanceof LifecycleHandler` check, zero impact when unused
- **Lookup is the bridge** — services receive `Lookup` from their callers,
  not from the framework directly
