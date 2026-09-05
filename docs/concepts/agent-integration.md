# AI Agent Integration

Status: current

Audience: application developers integrating agent-driven UI actions

The agent integration works with the same direct block components that serve
the UI. A block owns its local cache and declares what an agent may inspect
or request. A view only renders state and dispatches user intents; it has no
agent or event-publishing authority.

## Runtime Model

```text
PromptBlock
  -> AgentRuntime
  -> AgentService
  -> BlockProfile
  -> ActionDispatcher
  -> active BlockRuntime lookup
  -> typed framework event
  -> block or scene handler
```

`PromptBlock` is a `Block`. It owns the chat cache and the
`PromptService` bridge, watches scene/category context, and forwards the
mounted primary block notification to `AgentRuntime`.

`Scene` intentionally contains descriptors and navigation state only. When a
primary descriptor mounts, `DirectBlockHost` publishes
`PRIMARY_BLOCK_MOUNTED` with the live `BlockRuntime`. `AgentRuntime` accepts that
publication only when its descriptor ID matches the active scene. This prevents
an agent from acting on an unmounted or stale component.

## BlockRuntime Capabilities

Every `BlockRuntime` can expose two optional, current capabilities:

```java
public interface BlockRuntime {
    default BlockMetadata blockMetadata() { return null; }
    default List<BlockAction> agentActions() { return List.of(); }
}
```

`blockMetadata()` is structured, current state for reasoning. A list
block includes its schema, page, page size, exact total, sort, search, filters,
and visible items; a form block includes its schema, effective capabilities,
status, dirty flag, validation feedback, and current draft (excluding
password values), plus its loaded entity when applicable; a custom block can expose any
domain-relevant state.

`agentActions()` is the declarative action vocabulary. A `BlockAction`
contains an action name, typed `EventKey`, human-readable description, payload
schema, parser, and `DispatchEffect`.

The built-in direct bases expose the standard admin actions:

| BlockRuntime base              | Typical actions                                                                      |
|----------------------------|--------------------------------------------------------------------------------------|
| `ListBlock<T>` | permitted CRUD actions plus `page` and `select_all`                                |
| `FormBlock<T>` | permitted actions among `set_field`, `save`, and `cancel`                            |
| `EditBlock<T>` | permitted form actions plus `delete`                                                 |

`BlockProfile.of(block)` combines the live metadata and declared actions.
It identifies these bases with `ListBlock`,
`FormBlock`, and `EditBlock`; there is no parallel
block/view hierarchy to infer.

Browser confirmation dialogs are presentation safeguards, not agent protocol
steps. An agent delete dispatches the declared typed event directly; effective
capabilities and the block's in-flight mutation guard still apply before the
service is called.

## Defining An Agent Action

Blocks publish internally through their own mounted `Lookup`, but describe
their agent-facing actions as data:

```java
@Override
public List<BlockAction> agentActions() {
    return List.of(
            new BlockAction(
                    "archive",
                    ARCHIVE_REQUESTED,
                    "Archive the selected report",
                    new PayloadSchema.StringValue("report ID"),
                    DispatchEffect.SCENE_CHANGE));
}
```

The action's payload schema is the validation boundary. The agent supplies a
`BlockActionPayload`; `ActionDispatcher` parses it before publishing the
event. It never allows the model to name an arbitrary event key.

## Dispatch And Authorization

`ActionDispatcher` is the only agent-side event publisher. Given a declared
action, payload, active block, current lookup, and `ActionGate`, it:

1. evaluates allow, block, or confirmation rules;
2. parses the payload according to the declared schema;
3. publishes the action's typed event through `block.lookup()`;
4. places a completion fence after the event so the runtime can wait for
   framework processing.

`ActionGate` uses the existing authorization data in `Lookup`. A confirmation
result becomes a pending action; `AgentRuntime` only calls `dispatchDirect(...)`
after an explicit approval. The dispatcher sets a short-lived marker around
publication so the runtime can distinguish agent-originated events from real
user interaction.

## Navigation And Scene Changes

Navigation is separate from a block action:

```java
dispatcher.dispatchNavigate(PostsListBlock.class, lookup);
```

This publishes `SET_PRIMARY`. The scene selects a fresh descriptor, mounts the
component tree, and then announces the new primary block. For an action
whose `DispatchEffect` changes the scene, the runtime waits for that scene to
settle before the next plan step reads a new profile.

List pagination, sorting, searching, and filtering are block-local cache
changes coordinated with scene query state. The current list component updates
its own state immediately and watches URL context for browser back/forward,
while the scene maintains the effective URL. The agent profile exposes the
same query and exact total that the user sees. The standard action vocabulary
currently changes pages and selection/CRUD state; applications that want an
agent to change search or filters must declare an additional typed action
rather than allowing arbitrary query writes.

## Prompt Lifecycle

`AgentRuntime.submit(text)` runs the agent loop on a virtual thread. For each
step it materializes an `AgentContext` from:

- the active direct block and its current `BlockProfile`;
- the `StructureNode` tree built from composition groups;
- the current lookup and action filter;
- authorization and delegation services;
- the latest scene descriptor for settlement checks.

`AgentService` returns one of four results:

| Result | Runtime behavior |
| --- | --- |
| `TextReply` | send text to `PromptService` |
| `ActionResult` | gate and dispatch a declared block action |
| `NavigateResult` | publish `SET_PRIMARY` for the target block |
| `PlanResult` | process bounded natural-language steps sequentially |

The loop stops on a text reply, a dispatch failure, a pending confirmation or
approval, cancellation, policy interruption, or the configured loop limit.
User events observed on the mounted block can cancel an in-flight plan;
agent-dispatched events do not.

## Composition Wiring

Bind the prompt and approval components just like every other block:

```java
Group support = new Group()
        .bind(PromptBlock.class, () -> new PromptBlock(
                promptService, agentService, dispatcher, authorization, spawner, structure))
        .bind(DelegationApprovalBlock.class,
                () -> new DelegationApprovalBlock(delegationStore));
```

Place the prompt as a stable sidebar companion and the approval block as a
modal. The primary block can change without reconstructing the chat cache or
agent service bridge.

## Implementing An Agent Service

`AgentService` is application policy. The framework gives it a profile and
structure tree; it does not dictate prompt parsing.

```java
public final class MyAgentService extends AgentService {
    @Override
    public AgentResult handlePrompt(String prompt,
                                    BlockProfile profile,
                                    StructureNode structure) {
        // Convert domain language into TextReply, NavigateResult,
        // ActionResult, or PlanResult.
        return new AgentResult.TextReply("No matching action");
    }
}
```

The examples include `RegexAgentService` for deterministic validation and
optional LLM-backed implementations. Keep model-specific prompt construction
and transport inside the application service; keep blocks responsible for
their metadata, actions, and domain effects.

## Testing

Test agent behavior at three boundaries:

1. BlockRuntime tests verify current metadata and declared action schemas.
2. `ActionDispatcher` tests verify payload parsing, gate results, and emitted
   typed events.
3. `AgentRuntime` tests verify descriptor settlement, approval, interruption,
   navigation, and multi-step plans using a deterministic `AgentService`.

The agent must never need a view instance to reason about or act on a block.
