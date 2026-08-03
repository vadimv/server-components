# AI Agent Integration

The agent integration works with the same direct contract components that serve
the UI. A contract owns its local cache and declares what an agent may inspect
or request. A view only renders state and dispatches user intents; it has no
agent or event-publishing authority.

## Runtime Model

```text
PromptContract
  -> AgentRuntime
  -> AgentService
  -> ContractProfile
  -> ActionDispatcher
  -> active Contract lookup
  -> typed framework event
  -> contract or scene handler
```

`PromptContract` is a `ContractNodeComponent`. It owns the chat cache and the
`PromptService` bridge, watches scene/category context, and forwards the
mounted primary contract notification to `AgentRuntime`.

`Scene` intentionally contains descriptors and navigation state only. When a
primary descriptor mounts, `DirectContractHost` publishes
`PRIMARY_CONTRACT_MOUNTED` with the live `Contract`. `AgentRuntime` accepts that
publication only when its descriptor ID matches the active scene. This prevents
an agent from acting on an unmounted or stale component.

## Contract Capabilities

Every `Contract` can expose two optional, current capabilities:

```java
public interface Contract {
    default ContractMetadata contractMetadata() { return null; }
    default List<ContractAction> agentActions() { return List.of(); }
}
```

`contractMetadata()` is structured, current state for reasoning. A list
contract includes its schema, page, sort, and visible items; a form contract
includes its schema and current entity; a custom contract can expose any
domain-relevant state.

`agentActions()` is the declarative action vocabulary. A `ContractAction`
contains an action name, typed `EventKey`, human-readable description, payload
schema, parser, and `DispatchEffect`.

The built-in direct bases expose the standard admin actions:

| Contract base | Typical actions |
| --- | --- |
| `ListContractComponent<T>` | `create`, `edit`, `edit_selected`, `delete`, `delete_selected`, `page`, `select_all` |
| `FormContractComponent<T>` | `set_field`, `save`, `cancel` |
| `EditContractComponent<T>` | form actions plus `delete` |

`ContractProfile.of(contract)` combines the live metadata and declared actions.
It identifies these bases with `ListContractComponent`,
`FormContractComponent`, and `EditContractComponent`; there is no parallel
contract/view hierarchy to infer.

## Defining An Agent Action

Contracts publish internally through their own mounted `Lookup`, but describe
their agent-facing actions as data:

```java
@Override
public List<ContractAction> agentActions() {
    return List.of(
            new ContractAction(
                    "archive",
                    ARCHIVE_REQUESTED,
                    "Archive the selected report",
                    new PayloadSchema.StringValue("report ID"),
                    DispatchEffect.SCENE_CHANGE));
}
```

The action's payload schema is the validation boundary. The agent supplies a
`ContractActionPayload`; `ActionDispatcher` parses it before publishing the
event. It never allows the model to name an arbitrary event key.

## Dispatch And Authorization

`ActionDispatcher` is the only agent-side event publisher. Given a declared
action, payload, active contract, current lookup, and `ActionGate`, it:

1. evaluates allow, block, or confirmation rules;
2. parses the payload according to the declared schema;
3. publishes the action's typed event through `contract.lookup()`;
4. places a completion fence after the event so the runtime can wait for
   framework processing.

`ActionGate` uses the existing authorization data in `Lookup`. A confirmation
result becomes a pending action; `AgentRuntime` only calls `dispatchDirect(...)`
after an explicit approval. The dispatcher sets a short-lived marker around
publication so the runtime can distinguish agent-originated events from real
user interaction.

## Navigation And Scene Changes

Navigation is separate from a contract action:

```java
dispatcher.dispatchNavigate(PostsListContract.class, lookup);
```

This publishes `SET_PRIMARY`. The scene selects a fresh descriptor, mounts the
component tree, and then announces the new primary contract. For an action
whose `DispatchEffect` changes the scene, the runtime waits for that scene to
settle before the next plan step reads a new profile.

List pagination and sorting are contract-local cache changes coordinated with
scene query state. The current list component updates its own state immediately
and watches URL context for browser back/forward, while the scene maintains the
effective URL. The agent therefore sees the same page and sort that the user
sees.

## Prompt Lifecycle

`AgentRuntime.submit(text)` runs the agent loop on a virtual thread. For each
step it materializes an `AgentContext` from:

- the active direct contract and its current `ContractProfile`;
- the `StructureNode` tree built from composition groups;
- the current lookup and action filter;
- authorization and delegation services;
- the latest scene descriptor for settlement checks.

`AgentService` returns one of four results:

| Result | Runtime behavior |
| --- | --- |
| `TextReply` | send text to `PromptService` |
| `ActionResult` | gate and dispatch a declared contract action |
| `NavigateResult` | publish `SET_PRIMARY` for the target contract |
| `PlanResult` | process bounded natural-language steps sequentially |

The loop stops on a text reply, a dispatch failure, a pending confirmation or
approval, cancellation, policy interruption, or the configured loop limit.
User events observed on the mounted contract can cancel an in-flight plan;
agent-dispatched events do not.

## Composition Wiring

Bind the prompt and approval components just like every other contract:

```java
Group support = new Group()
        .bind(PromptContract.class, () -> new PromptContract(
                promptService, agentService, dispatcher, authorization, spawner, structure))
        .bind(DelegationApprovalContract.class,
                () -> new DelegationApprovalContract(delegationStore));
```

Place the prompt as a stable sidebar companion and the approval contract as a
modal. The primary contract can change without reconstructing the chat cache or
agent service bridge.

## Implementing An Agent Service

`AgentService` is application policy. The framework gives it a profile and
structure tree; it does not dictate prompt parsing.

```java
public final class MyAgentService extends AgentService {
    @Override
    public AgentResult handlePrompt(String prompt,
                                    ContractProfile profile,
                                    StructureNode structure) {
        // Convert domain language into TextReply, NavigateResult,
        // ActionResult, or PlanResult.
    }
}
```

The examples include `RegexAgentService` for deterministic validation and
optional LLM-backed implementations. Keep model-specific prompt construction
and transport inside the application service; keep contracts responsible for
their metadata, actions, and domain effects.

## Testing

Test agent behavior at three boundaries:

1. Contract tests verify current metadata and declared action schemas.
2. `ActionDispatcher` tests verify payload parsing, gate results, and emitted
   typed events.
3. `AgentRuntime` tests verify descriptor settlement, approval, interruption,
   navigation, and multi-step plans using a deterministic `AgentService`.

The agent must never need a view instance to reason about or act on a contract.
