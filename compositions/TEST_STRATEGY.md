# Compositions Test Strategy Research

## What Already Exists

### Deterministic Event Loop

The core module has a `ManualEventLoop` implementing `EventLoop` that allows step-by-step, synchronous event processing. No web server needed. Used in `LivePageSessionTests` and `ReactorTests`.

```java
static class ManualEventLoop implements EventLoop {
    private Runnable step;

    @Override
    public void start(final Runnable logic) { this.step = logic; }

    @Override
    public void stop() { /* ... */ }

    public void runOneStep() { step.run(); }
}
```

### Message Capture

`TestCollectingRemoteOut` records all outbound commands (DOM diffs, JS evals, history pushes) as typed sealed-interface records. This is effectively a "virtual browser" output sink.

### Test Lookup

`TestLookup` provides in-memory context injection with event publish/subscribe tracking (`wasPublished()`, `getLastPublishedPayload()`).

### Property-Based Testing

jqwik is already a dependency. `CompositionsPropertyTests` has PBT for Router matching and DataSchema invariants. It is annotated with `@Disabled`, but jqwik ignores JUnit's `@Disabled` and still executes the properties when discovered. Core has PBT for DOM tree diff reversibility and HTML DSL correctness.

### Unit-Level Contract Tests

ViewContract, ListView, CreateView, EditView, routing, CapabilityBus, Composition/Group assembly all have JUnit 5 tests.

---

## Gaps

### Gap 1: No Integration-Level Simulated Session for Compositions

`LivePageSessionTests` operates at the core level (raw DOM events, component segments). There is no equivalent that operates at the compositions level -- bootstrapping a `CrudApp` or `Composition` with a `Router`, `Layout`, `Group` hierarchy, then simulating user navigation through the `ManualEventLoop`.

**What's needed:** A `CompositionSessionHarness` that:
- Creates a full composition (Router + Layout + Groups) without HTTP
- Wraps `LivePageSession` + `ManualEventLoop`
- Exposes high-level operations: `navigateTo(path)`, `clickElement(path)`, `submitForm(data)`, `fireEvent(SHOW, contract)`
- Captures state snapshots after each step for invariant checking

### Gap 2: No Stateful PBT / State Machine Testing

The existing PBT tests are stateless -- they verify pure functions (path matching, schema transforms). For exploring state space and finding edge cases in navigation/CRUD/agent interactions, stateful property-based testing is needed.

jqwik supports this via `@Property` + `ActionChain` / state machine model:

```java
// Conceptual sketch
@Property
void composition_invariants_hold_under_arbitrary_action_sequences(
        @ForAll("actions") ActionChain<CompositionModel> chain) {
    chain.run();
}
```

Where actions are drawn from: `Navigate(path)`, `Show(contract)`, `Hide(contract)`, `SetPrimary(contract)`, `ActionSuccess`, `SubmitForm(data)`, `AgentDelegate(grant)`, `Back`, `Forward`.

After each action, check invariants:
- Exactly one routed contract is active (or zero if no route matches)
- No destroyed contracts receive events
- Navigation history is consistent with URL state
- ABAC authorization decisions are monotonically enforced (no privilege escalation)
- Lazy contracts are only instantiated after SHOW events, except URL-routed overlays which are pre-activated during scene build
- Layer stack depth matches active overlays

### Gap 3: No Deterministic Simulation Testing (DST)

DST (as popularized by FoundationDB, TigerBeetle, Antithesis) takes PBT further with:
- **Seeded pseudo-random scheduling** -- replay exact failure scenarios from a seed
- **Fault injection** -- simulate async callback failures, dropped events, reordered events
- **Time simulation** -- virtual clock instead of real time

The existing `ManualEventLoop` is the starting point. For full DST:

```java
class SimulatedEventLoop implements EventLoop {
    private final Random rng;                       // seeded for reproducibility
    private final PriorityQueue<TimedEvent> queue;  // virtual time

    void advanceTo(long virtualTimeMs);   // time travel
    void injectFault(FaultType type);     // drop, reorder, duplicate events
    void runUntilQuiescent();             // drain until no more pending
}
```

This would catch:
- Race conditions in event ordering (e.g., SHOW arriving before route resolution)
- State corruption from interleaved contract lifecycle events
- Agent delegation edge cases under concurrent actions

### Gap 4: No Invariant Oracle / Model Checker

The tests verify specific scenarios but there is no abstract model of what a correct composition state looks like. An oracle would be a simplified model:

```java
record CompositionModel(
    Path currentRoute,
    Set<Class<?>> activeContracts,   // what should be alive
    List<Class<?>> layerStack,       // overlay ordering
    Map<Class<?>, Lifecycle> states  // eager/lazy/destroyed
) {
    void assertConsistentWith(LiveCompositionState actual) { ... }
}
```

---

## Recommended Approach (Layered)

| Layer                        | Style                           | What It Tests                                                | Framework                                               |
|------------------------------|---------------------------------|--------------------------------------------------------------|---------------------------------------------------------|
| L0: Pure functions           | Stateless PBT                   | Router, Schema, Group resolution, StructureNode              | jqwik `@Property` (already started)                     |
| L1: Contract lifecycle       | Stateful PBT                    | Single contract: state transitions, event handling, auth     | jqwik `ActionChain` + `TestLookup`                      |
| L2: Composition session      | Stateful PBT + invariant oracle | Multi-contract navigation, CRUD flows, layer stacking        | `CompositionSessionHarness` + `ManualEventLoop` + jqwik |
| L3: Deterministic simulation | Seeded simulation               | Concurrency, fault tolerance, agent interactions under chaos | `SimulatedEventLoop` + custom runner                    |

---

## Where to Start

1. **Clean up the existing PBT tests** -- remove the misleading `@Disabled` from `CompositionsPropertyTests`, keep the suite as L0 smoke coverage, and avoid treating it as compositions-session coverage.

2. **Build the `CompositionSessionHarness`** -- the critical missing piece. It bridges the core's `ManualEventLoop` + `TestCollectingRemoteOut` with the compositions-level abstractions (Router, Scene, Contracts). Once this exists, both hand-written integration tests and PBT can use it.

3. **Define invariants first, then generate actions** -- the invariants are more valuable than the generators. Start with 3-4 hard invariants, then grow the action space incrementally.

4. **Add DST incrementally** -- first make agent and time-based flows injectable and deterministic, then swap `ManualEventLoop` for `SimulatedEventLoop` with seeded randomness. The harness stays the same; only the scheduling changes.

---

## Validation Notes

### What Was Verified

- The repo already supports no-server rendering at the application layer: `App.apply(HttpRequest)` returns the root component, so tests can construct requests directly without Jetty.
- The core already has the essential primitives for a virtual browser session:
  - `ManualEventLoop`
  - `LivePageSession`
  - `PageBuilder`
  - `TestCollectingRemoteOut`
- `CompositionsPropertyTests` currently runs under jqwik despite `@Disabled`.
- Browser-level CRUD and auth smoke coverage already exists in `examples`, but there is no in-memory compositions session layer between unit tests and Playwright.

### Important Corrections To The Initial Draft

- The immediate gap is not "enable property tests" but "add composition-session infrastructure".
- "Lazy contracts instantiate only after SHOW" is not universally true:
  URL-routed overlays are pre-activated by `SceneBuilder` and picked up by `LayerComponent`.
- Deterministic simulation of agent flows is not possible yet without refactoring:
  `PromptContract` uses real virtual threads and `PromptService` uses a real scheduler.

---

## CrudApp Test Matrix

The `examples` posts/comments app is the best target for the first compositions-session harness because it exercises routing, overlays, CRUD, auth, and agent-assisted flows in one place.

### A. Navigation And Routing

1. Initial route selection
- `/posts` selects `PostsListContract`
- `/comments` selects `CommentsListContract`
- `/posts/:id` auto-opens `PostEditContract` over the posts list
- `/comments/:id` auto-opens `CommentEditContract` over the comments list

2. Primary navigation
- `SET_PRIMARY(CommentsListContract)` updates routed contract and pushes `/comments`
- query params from `/posts?p=2&sort=desc` do not leak into `/comments`
- repeated `SET_PRIMARY` to the current contract is a no-op

3. Overlay navigation
- `SHOW(PostCreateContract)` opens layer 1 without rebuilding the primary list
- `SHOW(PostEditContract, {id})` opens edit overlay with `SHOW_DATA`
- nested `SHOW(DelegationApprovalContract)` while another overlay is open creates layer 2
- `HIDE` only closes the matching topmost contract

4. Route restoration
- cancel/save/delete from a URL-routed edit overlay restores the parent route
- parent route restores `fromP` and `fromSort` back to `p` and `sort`
- `UPDATE_PATH_ONLY` plus subsequent query changes keeps the correct pending base path

### B. CRUD Flows

1. Posts list
- page change updates URL query and keeps routed contract stable
- sort toggle updates URL query and keeps routed contract stable
- select all updates selection state and emits `SELECTION_CHANGED`

2. Post create
- `CREATE_ELEMENT_REQUESTED` opens `PostCreateContract`
- valid `FORM_SUBMITTED` creates a post, emits `ACTION_SUCCESS`, closes or navigates correctly, and refreshes the posts list
- invalid form submission does not emit `ACTION_SUCCESS` and keeps the form active
- cancel closes or navigates back without mutation

3. Post edit
- opening by route and opening by `SHOW` resolve the same entity ID correctly
- valid save refreshes routed list data
- delete removes the entity, closes/navigates correctly, and refreshes routed list data
- nonexistent ID does not corrupt scene state

4. Bulk operations
- bulk delete removes selected posts/comments and refreshes the primary list
- delete-selected with empty selection is a no-op

5. Mirror coverage for comments
- same create/edit/delete/bulk-delete flows should be covered for `Comment*Contract`

### C. Auth

1. Anonymous access
- simple auth redirects protected routes to `/auth/login?redirect=...`
- `/auth/login` remains public

2. Authenticated access
- request with valid auth cookie resolves directly to protected compositions
- sign-out triggers cookie clear and full reload

3. Authorization failures
- unauthorized contract during scene build fails cleanly and does not partially activate a scene

### D. Agent And Delegation

1. Prompt side panel baseline
- prompt view history reload survives scene remounts
- prompt messages do not duplicate after remount + event replay

2. Dispatch integration
- agent `NavigateResult` emits `SET_PRIMARY`
- agent action dispatch emits contract events and the completion fence resolves after handlers run
- blocked and confirmation-required actions do not publish contract events

3. Delegation approval
- when spawn requires approval, prompt flow opens `DelegationApprovalContract`
- approving delegation replays the queued prompt
- denying delegation closes the overlay and leaves the main scene intact

4. Plan execution
- multi-step plans stop when navigation lands on the wrong scene
- plan steps are capped
- scene-settle synchronization works under deterministic event stepping

---

## Proposed CompositionSessionHarness

### Goals

- Run without web server
- Drive the real component/runtime stack, not a fake model
- Expose ergonomic semantic operations for PBT
- Still allow lower-level DOM/evalJs assertions when needed

### Construction

Conceptually:

```java
CompositionSessionHarness harness = CompositionSessionHarness.start(
    new App(config, compositions, services),
    RequestSpec.get("/posts?p=2"),
    SessionSpec.anonymous()
);
```

Internally it should:
- create an `HttpRequest`
- call `App.apply(request)`
- render into `PageBuilder`
- start `LivePageSession` with `ManualEventLoop`
- capture outbound commands via `TestCollectingRemoteOut`

### Suggested API Surface

#### 1. Semantic API

This is the main API for stateful PBT because it is stable and high-signal.

```java
harness.navigateTo("/comments");
harness.publish(EventKeys.SET_PRIMARY, CommentsListContract.class);
harness.show(PostCreateContract.class, Map.of());
harness.hide(PostCreateContract.class);
harness.submitForm(Map.of("title", "Hello", "content", "World"));
harness.deleteCurrent();
harness.confirmJs(true);
harness.agentPrompt("delete post 1");
harness.approveDelegation(true);
harness.runUntilQuiescent();
```

#### 2. Session/Browser API

Use this for a smaller set of tests validating wiring to the virtual browser.

```java
harness.dispatchDomClick(TreePositionPath.of("..."));
harness.replyToEvalJs(descriptor, new JsonDataType.Boolean(true));
harness.remoteCommands();
harness.lastPushHistory();
harness.listenedEvents();
```

#### 3. Inspection API

Needed for invariants and debugging.

```java
harness.currentPath();
harness.currentQuery();
harness.routedContractClass();
harness.activeLayerContracts();
harness.remoteCommandLog();
harness.renderExceptions();
harness.snapshot();
```

### Snapshot Shape

```java
record CompositionSnapshot(
    String path,
    Map<String, String> query,
    Class<?> routedContract,
    List<Class<?>> activeLayers,
    List<Object> remoteCommands,
    boolean hasRenderErrors
) {}
```

This does not need to expose every internal detail on day 1. It only needs enough to support the first invariant set.

---

## Recommended Invariants For First Stateful Tests

### Core Invariants

1. Routed contract and URL stay aligned
- current path matches the routed contract's configured route family

2. Overlay stack is consistent
- each active layer has exactly one active contract
- `HIDE(X)` only removes `X` when `X` is the active contract of that layer

3. Successful mutations refresh the primary view
- after create/edit/delete/bulk-delete success, the routed contract is rebuilt or the route is restored as expected

4. No zombie contracts
- after a contract is hidden or replaced, later events do not mutate it

5. Auth and delegation are monotonic
- denied access does not later become effective without an explicit state change such as login or approval

### Secondary Invariants

6. Query param hygiene
- moving across primary contracts does not carry unrelated query params

7. Parent-route restoration
- routed overlay close restores the correct list route and query params

8. Prompt history continuity
- prompt messages survive scene rebuilds without duplication

---

## Practical Rollout

### Phase 1: Harness + Handwritten Integration Tests

Build the harness and cover:
- primary navigation
- overlay open/close
- create/save/cancel/delete
- URL-routed overlay restore
- simple auth redirect and authenticated access

### Phase 2: Stateful PBT On Semantic Actions

Generate short action sequences over:
- navigate
- set primary
- show
- hide
- submit form
- delete
- page change
- sort change
- approve/deny delegation

Start with bounded sequence lengths and one app fixture (`CrudApp`).

### Phase 3: Deterministic Simulation

Introduce injectable abstractions for:
- executor / thread spawning
- scheduler / time source
- approval source
- agent service latency / callback ordering

Then add:
- seeded scheduler choices
- delayed completion
- dropped or duplicated synthetic callbacks
- virtual time advancement

---

## When To Run What

This repo already has a de facto split:
- default Maven / CI run: `*Test` and `*Tests`
- manual broader run: `-Ptest-all`, which also includes `*IT`

Right now that means:
- unit tests and current property tests run on normal `mvn test` / `mvn package`
- Playwright and other `IT` tests are excluded by default and run manually
- there is not yet a dedicated bucket for slow PBT or DST

### Current Repo Behavior

- GitHub Actions runs `mvn -B package --file pom.xml`
- Because the default build does not include `*IT`, browser tests are not part of PR/main CI today
- The `test-all` profile adds `**/*IT.java` into Surefire

This works, but it compresses several different test economics into only two modes:
- default
- everything manual

For the compositions work, that will become too coarse once stateful PBT and DST exist.

### Recommended Suite Tiers

#### Tier 0: Fast Targeted

What belongs here:
- single-class unit tests
- small local contract tests
- small property tests with low cost and no heavy setup

Expected usage:
- while coding
- before each small commit
- on save or in IDE

Examples in this repo:
- `RouterTests`
- `CreateViewContractTests`
- `EditViewContractTests`
- `ActionDispatcherTests`
- current small jqwik suites like `NodesTreeDiffPropertyTests` and `CompositionsPropertyTests`

Guidance:
- should finish in seconds
- should run frequently enough that developers do not hesitate to trigger them

#### Tier 1: Fast Full

What belongs here:
- all normal unit tests across modules
- fast property tests across modules
- future compositions-session tests that are deterministic and still cheap

Expected usage:
- before push
- on every PR
- on every merge to main

Goal:
- strong regression protection with low enough latency to stay mandatory

This should become the default quality gate.

#### Tier 2: Slow Deterministic

What belongs here:
- stateful PBT with longer action sequences
- seeded simulation tests with multiple seeds
- deterministic no-browser integration suites that are too expensive for every edit-run

Expected usage options:
- before opening a PR for risky changes
- on every PR for touched subsystems only
- always on merge to main
- nightly on full breadth

Why separate this tier:
- these tests are still far cheaper and more diagnosable than Playwright
- they are ideal for finding deep lifecycle/order bugs
- but their runtime will likely be too high for the tight inner loop

#### Tier 3: Browser E2E

What belongs here:
- Playwright smoke tests
- auth flow tests
- URL / browser history / DOM wiring validation
- cross-browser runs if desired

Expected usage options:
- manually during feature development when working on UI behavior
- automatically on PR for affected areas
- always on merge to main
- nightly full suite
- release candidate / pre-release gates

These are the highest-cost, highest-fidelity tests.

#### Tier 3.5: Browser Exploration

What belongs here:
- seed-driven Playwright workflows
- generated but short browser action sequences
- browser-level state exploration over a very small action alphabet

Examples:
- navigate, back, forward
- open create/edit overlay
- save, cancel, delete
- auth redirect/login/logout

Important note:
- this is a useful hybrid layer, but it should usually be thought of as `e2e-explore`, not true DST
- once a real browser, real timing, and real threads are involved, determinism becomes much weaker

Expected usage options:
- manual reproduction of tricky UI bugs
- narrow PR checks for high-risk browser behavior
- nightly exploratory runs with fixed seeds

Why keep it separate:
- it is relatively low-hanging fruit because the repo already has Playwright smoke tests
- it gives more real-stack exploration quickly
- but it is much more expensive than session-level exploration and usually less reproducible than in-memory deterministic tests

#### Tier 4: Long-Running Exploration

What belongs here:
- large-seed DST campaigns
- long-horizon stateful generators
- soak-like deterministic exploration
- optionally a very small number of fixed-seed `e2e-explore` campaigns

Expected usage:
- nightly
- scheduled main-branch jobs
- pre-release hardening
- bug reproduction and minimization

These are not developer inner-loop tests. Their job is to discover rare failures, not to provide fast feedback.

### About E2E-DST Hybrids

There are two different ideas that are easy to conflate:

1. Browser state exploration
- Playwright drives many generated workflows through the real app
- good for auth, history, URL sync, overlay wiring, and reproducing rare UI bugs

2. True DST with a browser boundary
- app time, scheduling, and failures are still controlled deterministically
- the browser is only one client of a deterministic system

The first is realistic and relatively low-hanging fruit here.
The second is much harder and should not be assumed just because the browser actions are seed-driven.

Recommendation:
- use `session + pbt + dst` as the main state-exploration engine
- add a smaller `e2e-explore` layer to confirm browser wiring and user-visible behavior
- do not make broad Playwright exploration the primary exploration mechanism

This means:
- `e2e-explore` is a good starting point
- true `e2e-dst` should be treated as a future possibility only if the runtime becomes deterministic enough under the browser boundary

---

## Development Cycle Options

There is no single correct cadence. A good choice depends on team size, CI budget, and tolerance for slower PRs. Below are practical options from lighter to stricter.

### Option A: Conservative And Cheap

Use when:
- the project is still building out test infrastructure
- CI minutes are limited
- E2E is expensive or occasionally brittle

Cadence:
- During coding: run Tier 0
- Before push: run touched-module Tier 1
- PR CI: run full Tier 1
- Nightly: run Tier 2 and Tier 3
- Release / pre-release: run full Tier 2, Tier 3, and long Tier 4

Pros:
- very fast PR feedback
- cheapest CI
- easiest to adopt now

Cons:
- deeper lifecycle bugs may be caught only after merge or overnight

### Option B: Balanced Default

Use when:
- unit and fast deterministic tests are reliable
- browser tests are valuable but too slow for every single PR by default

Cadence:
- During coding: Tier 0
- Before push: touched-module Tier 1, optionally targeted Tier 2 for risky work
- PR CI:
  - always run Tier 1
  - run selected Tier 2 based on changed areas
  - run a very small Tier 3 smoke subset for changed frontend/compositions/auth code
- Merge to main:
  - full Tier 1
  - broader Tier 2
  - key Tier 3 smoke flows
- Nightly:
  - full Tier 2
  - full Tier 3
  - Tier 4 exploration

Pros:
- strong confidence before merge
- contains CI cost
- usually the best tradeoff for a project like this

Cons:
- requires some suite classification and selective triggering logic

### Option C: Strict Gating

Use when:
- the project is high-risk or release-critical
- CI capacity is acceptable
- flakiness is low

Cadence:
- During coding: Tier 0
- Before push: Tier 1 and relevant Tier 2
- PR CI: Tier 1 + Tier 2 + Tier 3 smoke
- Merge to main: same or larger
- Nightly: full Tier 3 and Tier 4

Pros:
- most defects are caught before merge

Cons:
- slower PR cycle
- higher CI cost
- more likely to frustrate developers if slow suites are not well-engineered

For this repo, Option B looks like the best target state.

---

## Suggested Cadence For This Repo

### 1. Inner Loop During Coding

Run:
- targeted unit tests in touched modules
- targeted fast PBTs

Examples:
- editing router / scene / contract code:
  run `compositions`, `core`, and `ai-agent` targeted tests
- editing posts CRUD contracts:
  run `examples` contract/service tests plus relevant compositions tests

Goal:
- feedback in seconds, not minutes

### 2. Before Push

Run:
- all unit tests and fast PBT in affected modules
- targeted no-browser composition-session tests once the harness exists

Trigger especially when changing:
- routing
- scene lifecycle
- overlay logic
- contract base classes
- prompt/agent dispatch logic

### 3. PR CI

Mandatory:
- full Tier 1 across the repo

Recommended once the new suites exist:
- selected Tier 2 based on change scope

Examples of selective escalation:
- `core` / `compositions` lifecycle changes:
  run composition-session stateful tests
- `PromptContract`, `ActionDispatcher`, auth, or route-sync changes:
  run targeted slow deterministic suites
- view/browser protocol changes:
  run small Playwright smoke subset

### 4. Main-Branch Or Post-Merge CI

Run:
- full Tier 1
- broader Tier 2 than PR CI
- a small but representative Playwright smoke pack

This is a good place to catch cross-module interactions without slowing every PR equally.

### 5. Nightly / Scheduled

Run:
- full slow PBT
- full deterministic simulation matrix
- full Playwright suite
- optional multi-seed DST exploration

Nightly is the right place for:
- larger jqwik sample counts
- longer action chains
- many simulation seeds
- auth/browser matrix expansion

### 6. Release / Pre-Release

Run:
- everything in Tier 1, 2, and 3
- a broadened Tier 4 campaign if available

This should be the highest-confidence run, not the most frequent one.

---

## Change-Based Triggers

A useful way to keep CI cost under control is to trigger suites based on what changed.

### If `core` Runtime Changes

Run:
- all `core` unit tests
- core PBT
- composition-session tests
- selected Playwright smoke

Reason:
- event loop, session, DOM diff, and browser command protocol are foundational

### If `compositions` Lifecycle / Routing Changes

Run:
- all `compositions` unit tests
- fast PBT
- composition-session tests
- targeted slow stateful tests
- selected CRUD/auth Playwright smoke

### If `examples` Contracts / Services Change

Run:
- examples unit tests
- composition-session tests for `CrudApp`
- selected Playwright tests for touched feature area

### If Agent / Prompt Logic Changes

Run:
- `ai-agent` tests
- prompt/agent unit tests in `examples`
- deterministic prompt/delegation session tests
- targeted prompt/auth Playwright smoke

### If Styling / View DOM Wiring Changes

Run:
- unit tests
- targeted composition-session browser-protocol tests
- Playwright smoke for affected screens

---

## Practical Classification Advice

Once more suites exist, the repo will benefit from explicit classification rather than only relying on file suffixes.

### Define Test Kind By Metadata, Not Naming Alone

Theoretical test kind should be defined by semantics, not by filename.

Useful semantic kinds for this repo:
- `unit`: pure or local logic, no server, no browser
- `session`: in-memory runtime tests using the page/runtime stack without a real browser
- `pbt`: generated inputs or generated action sequences
- `dst`: deterministic simulation with controlled scheduling/time/faults
- `e2e`: real server plus real browser automation
- `e2e-explore`: generated or seeded browser workflows over the real stack

Speed and runtime policy should be separate attributes:
- `fast`
- `slow`
- optionally `nightly`

This lets one test belong to multiple kinds at once:
- `unit + fast`
- `pbt + fast`
- `session + pbt + slow`
- `dst + slow`
- `e2e + slow`
- `e2e-explore + slow`

### Recommended JUnit Tags

Consider adding JUnit tags such as:
- `unit`
- `session`
- `pbt`
- `dst`
- `e2e`
- `e2e-explore`
- `fast`
- `slow`
- `nightly`

Why:
- suffixes separate `IT` from non-`IT`, but not enough beyond that
- tags let you distinguish semantic kind from runtime cost
- tags let one suite participate in multiple policies without renaming classes

Example:

```java
@Tag("session")
@Tag("pbt")
@Tag("slow")
class CompositionStatefulPropertyTests {
}
```

### Naming Still Helps, But As A Secondary Convention

Naming is still useful for readability and coarse discovery. A reasonable convention would be:
- `*Tests` for ordinary unit-style suites
- `*PropertyTests` for PBT
- `*SessionTests` for deterministic in-memory runtime tests
- `*DstTests` for deterministic simulation suites
- `*IT` for browser or external integration tests

This is helpful for humans, but selection policy should come from tags and build stages rather than names alone.

### What This Means For The Current Repo

The current `*IT` convention is fine as a coarse boundary for browser/manual integration tests, but it is not expressive enough for the next phase.

It cannot cleanly distinguish:
- fast PBT vs slow PBT
- deterministic session tests vs browser E2E
- browser smoke vs browser exploration
- true DST vs non-deterministic exploratory browser runs

### Better Maven Separation

The current `test-all` profile pulls `*IT` into Surefire. That is workable, but not ideal long-term.

A more maintainable split would be:
- Surefire: unit tests, fast PBT, deterministic fast session tests
- Failsafe: browser E2E, browser exploration, and possibly the slowest deterministic suites

Benefits:
- clearer lifecycle separation
- easier CI stages
- easier retry / isolation policies

---

## Recommended End State

If the compositions testing vision is implemented, a good steady-state policy would be:

- Local developer inner loop:
  targeted unit tests + fast PBT
- Pre-push:
  affected-module unit tests + fast PBT + targeted fast composition-session tests
- PR CI:
  full unit tests + fast PBT + selected slow deterministic tests
- Main / post-merge:
  broader deterministic suites + small browser smoke pack
- Nightly:
  full slow PBT + DST + full Playwright
- Release:
  full matrix

That keeps the fast deterministic tests doing most of the heavy lifting, while reserving Playwright and long exploration campaigns for moments where their higher cost is justified.

---

## References

- **FoundationDB / Antithesis model**: deterministic simulation with fault injection; the `ManualEventLoop` is already 60% of the way there
- **jqwik stateful testing**: `@Property` + `Action<M>` + `ActionChain<M>` -- native to the existing test stack
- **TLA+ / Alloy style**: define the state machine formally first, then translate to executable tests -- useful for getting the invariants right before writing generators
