package rsp.compositions.agent;

import org.junit.jupiter.api.Test;
import rsp.component.EventKey;
import rsp.component.Lookup;
import rsp.compositions.agent.AgentService.AgentResult;
import rsp.compositions.authorization.AccessDecision;
import rsp.compositions.authorization.AccessPolicy;
import rsp.compositions.authorization.Attributes;
import rsp.compositions.authorization.Authorization;
import rsp.compositions.authorization.DelegationGrant;
import rsp.compositions.contract.ActionBindings;
import rsp.compositions.contract.ContractAction;
import rsp.compositions.contract.ContractActionPayload;
import rsp.compositions.contract.DispatchEffect;
import rsp.compositions.contract.EventKeys;
import rsp.compositions.contract.ViewContract;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Behavioral invariants for {@link AgentRuntime}.
 * <p>
 * Each test names the invariant being guarded; assertion bodies stay minimal so
 * the invariant is readable at a glance. Paths through {@link AgentRuntime#submit(String)}
 * spawn a virtual thread for the LLM call — those tests use a {@link CountDownLatch}
 * in the recording feedback to wait for a specific message. Synchronous paths
 * (yes/no confirmation routing, queued-result blocking, approval rejection)
 * assert directly without coordination.
 */
class AgentRuntimeTests {

    private static final long ASYNC_TIMEOUT_SECONDS = 5;

    // ------------------------------------------------------------------
    // Lazy spawn semantics
    // ------------------------------------------------------------------

    /** Invariant 1a: pre-session, an Allow decision executes without spawning. */
    @Test
    void submit_textReplyAllowed_executesWithoutSpawning() throws InterruptedException {
        RecordingFeedback feedback = new RecordingFeedback(m -> m.equals("hello"));
        ScriptedAgentService service = new ScriptedAgentService(
                new AgentResult.TextReply("hello"));

        AgentRuntime runtime = newRuntime(service, new ActionDispatcher(),
                new FailingSpawner(), allowAuthorization(), feedback, new TestLookup());

        runtime.submit("hi");

        assertTrue(feedback.await(ASYNC_TIMEOUT_SECONDS),
                "expected text reply to reach feedback within timeout");
    }

    /** Invariant 1b: pre-session Deny + RequiresApproval → result is queued,
     *  delegation modal is shown, "This requires your approval…" is sent. */
    @Test
    void submit_actionDenied_noSession_queuesResultAndPublishesShow() throws InterruptedException {
        RecordingFeedback feedback = new RecordingFeedback(
                m -> m.contains("requires your approval"));
        EventKey.VoidKey actionKey = new EventKey.VoidKey("test.create");
        ContractAction action = new ContractAction("create", actionKey, "Create");
        ScriptedAgentService service = new ScriptedAgentService(
                new AgentResult.ActionResult(action, ContractActionPayload.EMPTY));
        ScriptedSpawner spawner = new ScriptedSpawner(
                new SpawnResult.RequiresApproval("ticket-1", "needs approval"));
        TestLookup runtimeLookup = new TestLookup();

        AgentRuntime runtime = newRuntime(service, new ActionDispatcher(), spawner,
                denyAuthorization("denied-without-session"), feedback, runtimeLookup);

        runtime.submit("create something");

        assertTrue(feedback.await(ASYNC_TIMEOUT_SECONDS));
        assertNotNull(getField(runtime, "queuedResult"),
                "result must be queued for post-approval execution");
        assertTrue(runtimeLookup.wasPublished(EventKeys.SHOW),
                "delegation approval modal must be shown via SHOW event");
        ActionBindings.ShowPayload payload =
                runtimeLookup.getLastPublishedPayload(EventKeys.SHOW);
        assertEquals(StubContract.class, payload.contractClass());
    }

    /** Invariant 1c: with an active session, Deny is a real refusal — no
     *  second spawn attempt, message is "Action not permitted: …". */
    @Test
    void submit_actionDeniedWithActiveSession_refusesWithoutAttemptingSpawn()
            throws InterruptedException {
        RecordingFeedback feedback = new RecordingFeedback(
                m -> m.startsWith("Action not permitted"));
        EventKey.VoidKey actionKey = new EventKey.VoidKey("test.delete");
        ContractAction action = new ContractAction("delete", actionKey, "Delete");
        ScriptedAgentService service = new ScriptedAgentService(
                new AgentResult.ActionResult(action, ContractActionPayload.EMPTY));

        AgentRuntime runtime = newRuntime(service, new ActionDispatcher(),
                new FailingSpawner(), denyAuthorization("not authorized"),
                feedback, new TestLookup());
        setField(runtime, "agentSession", validSession());

        runtime.submit("delete it");

        assertTrue(feedback.await(ASYNC_TIMEOUT_SECONDS));
        String last = feedback.messages.get(feedback.messages.size() - 1);
        assertTrue(last.contains("not authorized"),
                "denial reason must be surfaced (got: " + last + ")");
    }

    // ------------------------------------------------------------------
    // Single queued result invariant
    // ------------------------------------------------------------------

    /** Invariant 2: while a queued result is awaiting approval, new submits
     *  must not invoke the LLM and must reply "Still awaiting…". */
    @Test
    void submit_whileQueuedResult_repliesAwaitingAndDoesNotInvokeService() {
        ScriptedAgentService service = new ScriptedAgentService();
        RecordingFeedback feedback = new RecordingFeedback();
        AgentRuntime runtime = newRuntime(service, new ActionDispatcher(),
                new FailingSpawner(), allowAuthorization(), feedback, new TestLookup());

        setField(runtime, "queuedResult", new AgentResult.TextReply("queued"));

        runtime.submit("anything");

        assertEquals(1, feedback.messages.size());
        assertTrue(feedback.messages.get(0).contains("Still awaiting"));
        assertEquals(0, service.callCount.get());
    }

    // ------------------------------------------------------------------
    // Pending-confirmation routing
    // ------------------------------------------------------------------

    /** Invariant 3a: "yes" after AwaitingConfirmation calls dispatchDirect
     *  on the gated action (skipping the LLM and the gate). */
    @Test
    void submit_yes_dispatchesDirectly_andClearsPendingConfirm() {
        ScriptedAgentService service = new ScriptedAgentService();
        RecordingDispatcher dispatcher = new RecordingDispatcher();
        AgentRuntime runtime = newRuntime(service, dispatcher, new FailingSpawner(),
                allowAuthorization(), new RecordingFeedback(), new TestLookup());

        ContractAction action = new ContractAction("do",
                new EventKey.VoidKey("test.do"), "Do it");
        setField(runtime, "pendingConfirm", pendingAction(action));

        runtime.submit("yes");

        assertEquals(List.of("do"), dispatcher.directCalls,
                "yes must invoke dispatchDirect on the queued action");
        assertNull(getField(runtime, "pendingConfirm"));
        assertEquals(0, service.callCount.get(),
                "agent service must not be invoked when answering a confirm");
    }

    /** Invariant 3b: "no" after AwaitingConfirmation cancels and clears, no dispatch. */
    @Test
    void submit_no_cancels_andClearsPendingConfirm() {
        ScriptedAgentService service = new ScriptedAgentService();
        RecordingDispatcher dispatcher = new RecordingDispatcher();
        RecordingFeedback feedback = new RecordingFeedback();
        AgentRuntime runtime = newRuntime(service, dispatcher, new FailingSpawner(),
                allowAuthorization(), feedback, new TestLookup());

        ContractAction action = new ContractAction("do",
                new EventKey.VoidKey("test.do"), "Do it");
        setField(runtime, "pendingConfirm", pendingAction(action));

        runtime.submit("no");

        assertEquals(List.of("Cancelled."), feedback.messages);
        assertNull(getField(runtime, "pendingConfirm"));
        assertTrue(dispatcher.directCalls.isEmpty(), "no must not dispatch");
        assertEquals(0, service.callCount.get());
    }

    // ------------------------------------------------------------------
    // Approval flow
    // ------------------------------------------------------------------

    /** Invariant 4a: approval rejected → queued result is dropped, denied
     *  message sent, no spawn attempted, no execution. */
    @Test
    void onApprovalDecided_false_clearsQueueAndSendsDenied() {
        RecordingFeedback feedback = new RecordingFeedback();
        ScriptedAgentService service = new ScriptedAgentService();
        AgentRuntime runtime = newRuntime(service, new ActionDispatcher(),
                new FailingSpawner(), allowAuthorization(), feedback, new TestLookup());

        setField(runtime, "queuedResult", new AgentResult.TextReply("queued"));

        runtime.onApprovalDecided(false);

        assertNull(getField(runtime, "queuedResult"));
        assertEquals(List.of("Agent delegation denied."), feedback.messages);
        assertEquals(0, service.callCount.get());
    }

    // ------------------------------------------------------------------
    // Scene settle
    // ------------------------------------------------------------------

    /** Invariant 5: onScene completes any pending settle future used by
     *  plan navigation. Repeated calls are safe. */
    @Test
    void onScene_completesPendingSettleFuture() {
        AgentRuntime runtime = newRuntime(new ScriptedAgentService(),
                new ActionDispatcher(), new FailingSpawner(), allowAuthorization(),
                new RecordingFeedback(), new TestLookup());

        CompletableFuture<Object> future = new CompletableFuture<>();
        setField(runtime, "sceneSettleFuture", future);

        runtime.onScene(null);

        assertTrue(future.isDone(), "future should complete on first scene push");

        // Second push must not throw or attempt a re-completion that errors.
        runtime.onScene(null);
        assertFalse(future.isCompletedExceptionally());
    }

    // ------------------------------------------------------------------
    // Multi-step loop (Phase 1B)
    // ------------------------------------------------------------------

    /** Loop invariant A: a non-plan dispatch is single-shot — the loop
     *  dispatches once and stops. (Multi-step intent must travel through a
     *  PlanResult; see the plan-enqueue test below.) */
    @Test
    void loop_singleAction_dispatchesOnceAndStops() throws InterruptedException {
        EventKey.VoidKey key = new EventKey.VoidKey("test.act");
        ContractAction action = new ContractAction("act", key, "Act");
        ScriptedAgentService service = new ScriptedAgentService(
                new AgentResult.ActionResult(action, ContractActionPayload.EMPTY),
                new AgentResult.ActionResult(action, ContractActionPayload.EMPTY));
        ScriptedDispatcher dispatcher = new ScriptedDispatcher(
                a -> new ActionDispatcher.DispatchResult.Dispatched(
                        a, ContractActionPayload.EMPTY,
                        CompletableFuture.completedFuture(null)));
        RecordingFeedback feedback = new RecordingFeedback(m -> m.equals("Act"));

        AgentRuntime runtime = newRuntime(service, dispatcher, new FailingSpawner(),
                allowAuthorization(), feedback, new TestLookup());

        runtime.submit("do it");

        assertTrue(feedback.await(ASYNC_TIMEOUT_SECONDS),
                "expected single dispatch within timeout");
        // Brief grace for the runtime to finish post-dispatch bookkeeping
        // before we assert no further LLM calls happened.
        Thread.sleep(50);
        assertEquals(1, service.callCount.get(),
                "non-plan dispatch is single-shot — no re-prompt");
        assertEquals(List.of("act"), dispatcher.dispatchCalls);
    }

    /** Loop invariant B: AwaitingConfirmation terminates the loop and sets pendingConfirm. */
    @Test
    void loop_terminates_onAwaitingConfirmation_andPreservesPending() throws InterruptedException {
        EventKey.VoidKey key = new EventKey.VoidKey("test.confirm");
        ContractAction action = new ContractAction("delete", key, "Delete");
        ScriptedAgentService service = new ScriptedAgentService(
                new AgentResult.ActionResult(action, ContractActionPayload.EMPTY),
                new AgentResult.ActionResult(action, ContractActionPayload.EMPTY));
        ScriptedDispatcher dispatcher = new ScriptedDispatcher(
                a -> new ActionDispatcher.DispatchResult.AwaitingConfirmation(
                        "Are you sure?", a, ContractActionPayload.EMPTY));
        RecordingFeedback feedback = new RecordingFeedback(m -> m.equals("Are you sure?"));

        AgentRuntime runtime = newRuntime(service, dispatcher, new FailingSpawner(),
                allowAuthorization(), feedback, new TestLookup());

        runtime.submit("delete things");

        assertTrue(feedback.await(ASYNC_TIMEOUT_SECONDS));
        assertEquals(1, service.callCount.get(),
                "loop must not iterate past an AwaitingConfirmation");
        assertNotNull(getField(runtime, "pendingConfirm"),
                "pendingConfirm must be set so the next user input can answer yes/no");
    }

    /** Loop invariant C: Blocked dispatch terminates the loop. */
    @Test
    void loop_terminates_onBlockedDispatch() throws InterruptedException {
        EventKey.VoidKey key = new EventKey.VoidKey("test.blocked");
        ContractAction action = new ContractAction("blocked", key, "Blocked");
        ScriptedAgentService service = new ScriptedAgentService(
                new AgentResult.ActionResult(action, ContractActionPayload.EMPTY),
                new AgentResult.ActionResult(action, ContractActionPayload.EMPTY));
        ScriptedDispatcher dispatcher = new ScriptedDispatcher(
                a -> new ActionDispatcher.DispatchResult.Blocked("forbidden by gate"));
        RecordingFeedback feedback = new RecordingFeedback(m -> m.equals("forbidden by gate"));

        AgentRuntime runtime = newRuntime(service, dispatcher, new FailingSpawner(),
                allowAuthorization(), feedback, new TestLookup());

        runtime.submit("attempt forbidden thing");

        assertTrue(feedback.await(ASYNC_TIMEOUT_SECONDS));
        assertEquals(1, service.callCount.get(),
                "loop must not iterate past a Blocked dispatch");
    }

    /** Loop invariant D: LoopPolicy budget caps plan-step iteration count.
     *  A 4-step plan with budget(2) dispatches 2 steps, then the budget
     *  message terminates before consuming the rest. */
    @Test
    void loop_terminates_atStepBudget_whileDispatchingPlanSteps()
            throws InterruptedException {
        EventKey.VoidKey key = new EventKey.VoidKey("test.act");
        ContractAction action = new ContractAction("act", key, "Act");
        ScriptedAgentService service = new ScriptedAgentService(
                new AgentResult.PlanResult(List.of("s1", "s2", "s3", "s4"), ""),
                new AgentResult.ActionResult(action, ContractActionPayload.EMPTY),
                new AgentResult.ActionResult(action, ContractActionPayload.EMPTY),
                new AgentResult.ActionResult(action, ContractActionPayload.EMPTY),
                new AgentResult.ActionResult(action, ContractActionPayload.EMPTY));
        ScriptedDispatcher dispatcher = new ScriptedDispatcher(
                a -> new ActionDispatcher.DispatchResult.Dispatched(
                        a, ContractActionPayload.EMPTY,
                        CompletableFuture.completedFuture(null)));
        RecordingFeedback feedback = new RecordingFeedback(m -> m.contains("step budget"));

        AgentRuntime runtime = newRuntime(service, dispatcher, new FailingSpawner(),
                allowAuthorization(), feedback, new TestLookup(),
                LoopPolicy.budget(2));

        runtime.submit("plan four things");

        assertTrue(feedback.await(ASYNC_TIMEOUT_SECONDS),
                "expected budget message within timeout");
        assertEquals(2, dispatcher.dispatchCalls.size(),
                "budget(2) must allow exactly 2 dispatched steps before stopping");
    }

    /** Loop invariant E (never-stop policy): while a loop is running, a new
     *  submit is rejected with "Still processing previous prompt...". This
     *  was the default before Phase 3; now requires explicit never-stop policy. */
    @Test
    void submit_whileLoopRunning_withNeverPolicy_isRejected() {
        RecordingFeedback feedback = new RecordingFeedback();
        ScriptedAgentService service = new ScriptedAgentService();
        AgentRuntime runtime = newRuntime(service, new ActionDispatcher(),
                new FailingSpawner(), allowAuthorization(), feedback, new TestLookup(),
                InterruptionPolicy.never());

        AtomicBoolean running = (AtomicBoolean) getField(runtime, "running");
        running.set(true);

        runtime.submit("anything");

        assertEquals(List.of("Still processing previous prompt..."), feedback.messages);
        assertEquals(0, service.callCount.get(),
                "LLM must not be invoked when a loop is in progress (never policy)");
    }

    // ------------------------------------------------------------------
    // Interruption framework (Phase 3)
    // ------------------------------------------------------------------

    /** notifyEvent with USER origin under strictStop cancels the current token
     *  so the running loop exits at its next iteration boundary. */
    @Test
    void notifyEvent_user_withStrictStop_cancelsCurrentToken() {
        AgentRuntime runtime = newRuntime(new ScriptedAgentService(),
                new ActionDispatcher(), new FailingSpawner(),
                allowAuthorization(), new RecordingFeedback(), new TestLookup());

        AtomicBoolean running = (AtomicBoolean) getField(runtime, "running");
        running.set(true);
        AbortToken token = new AbortToken();
        setField(runtime, "currentToken", token);

        runtime.notifyEvent(EventOrigin.USER, null);

        assertTrue(token.isCancelled(),
                "strictStop policy must cancel the current token on USER events");
    }

    /** notifyEvent with AGENT origin does NOT cancel — the agent's own
     *  dispatches must not interrupt its own loop. */
    @Test
    void notifyEvent_agent_doesNotCancelEvenUnderStrictStop() {
        AgentRuntime runtime = newRuntime(new ScriptedAgentService(),
                new ActionDispatcher(), new FailingSpawner(),
                allowAuthorization(), new RecordingFeedback(), new TestLookup());

        AtomicBoolean running = (AtomicBoolean) getField(runtime, "running");
        running.set(true);
        AbortToken token = new AbortToken();
        setField(runtime, "currentToken", token);

        runtime.notifyEvent(EventOrigin.AGENT, null);

        assertFalse(token.isCancelled(),
                "AGENT-origin events must never cancel the loop");
    }

    /** notifyEvent under never policy ignores all events regardless of origin. */
    @Test
    void notifyEvent_user_withNeverPolicy_doesNotCancel() {
        AgentRuntime runtime = newRuntime(new ScriptedAgentService(),
                new ActionDispatcher(), new FailingSpawner(),
                allowAuthorization(), new RecordingFeedback(), new TestLookup(),
                InterruptionPolicy.never());

        AtomicBoolean running = (AtomicBoolean) getField(runtime, "running");
        running.set(true);
        AbortToken token = new AbortToken();
        setField(runtime, "currentToken", token);

        runtime.notifyEvent(EventOrigin.USER, null);

        assertFalse(token.isCancelled(),
                "never policy must allow USER events without cancelling");
    }

    /** notifyEvent when no loop is running is a no-op (no NPE, no errors). */
    @Test
    void notifyEvent_whenNotRunning_isNoOp() {
        AgentRuntime runtime = newRuntime(new ScriptedAgentService(),
                new ActionDispatcher(), new FailingSpawner(),
                allowAuthorization(), new RecordingFeedback(), new TestLookup());

        // running flag is false by default; currentToken is null.
        // Must not NPE on currentToken nor send any feedback.
        runtime.notifyEvent(EventOrigin.USER, null);
        runtime.notifyEvent(EventOrigin.AGENT, null);
    }

    /** ActionDispatcher sets {@code AGENT_DISPATCH} thread-local around its
     *  publishes so subscribers can tell agent dispatches from user events. */
    @Test
    void actionDispatcher_setsAndClears_agentDispatchFlag() {
        ActionDispatcher dispatcher = new ActionDispatcher();
        EventKey.VoidKey key = new EventKey.VoidKey("test.flag");
        ContractAction action = new ContractAction("flag", key, "Test flag");

        boolean[] observedInsidePublish = {false};
        TestLookup contractLookup = new TestLookup();
        contractLookup.subscribe(key, () -> observedInsidePublish[0] = ActionDispatcher.isAgentDispatch());
        StubLookupContract contract = new StubLookupContract(contractLookup);

        assertFalse(ActionDispatcher.isAgentDispatch(),
                "flag must be false outside any dispatch");

        dispatcher.dispatch(action, ContractActionPayload.EMPTY, contract, new TestLookup(),
                (a, p, l) -> new GateResult.Allow(a, p));

        assertTrue(observedInsidePublish[0],
                "AGENT_DISPATCH must be true during the agent's publish");
        assertFalse(ActionDispatcher.isAgentDispatch(),
                "AGENT_DISPATCH must be cleared in the dispatcher's finally block");
    }

    /** User-driven publish (no AGENT_DISPATCH set) on the active contract
     *  fires the monitor and cancels the running loop under strictStop. */
    @Test
    void userEventMonitor_userPublish_cancelsLoop() {
        AgentRuntime runtime = newRuntime(new ScriptedAgentService(),
                new ActionDispatcher(), new FailingSpawner(),
                allowAuthorization(), new RecordingFeedback(), new TestLookup());

        AtomicBoolean running = (AtomicBoolean) getField(runtime, "running");
        running.set(true);
        AbortToken token = new AbortToken();
        setField(runtime, "currentToken", token);

        TestLookup activeContractLookup = new TestLookup();
        runtime.rebindUserEventMonitor(StubLookupContract.class, activeContractLookup);

        // User-driven publish: SET_PRIMARY without AGENT_DISPATCH set.
        activeContractLookup.publish(EventKeys.SET_PRIMARY, StubLookupContract.class);

        assertTrue(token.isCancelled(),
                "user-driven SET_PRIMARY must cancel the running loop");
    }

    /** Agent dispatch on the same active contract does NOT trigger the monitor. */
    @Test
    void userEventMonitor_agentDispatch_doesNotCancelLoop() {
        ActionDispatcher dispatcher = new ActionDispatcher();
        AgentRuntime runtime = newRuntime(new ScriptedAgentService(),
                dispatcher, new FailingSpawner(),
                allowAuthorization(), new RecordingFeedback(), new TestLookup());

        AtomicBoolean running = (AtomicBoolean) getField(runtime, "running");
        running.set(true);
        AbortToken token = new AbortToken();
        setField(runtime, "currentToken", token);

        TestLookup activeContractLookup = new TestLookup();
        runtime.rebindUserEventMonitor(StubLookupContract.class, activeContractLookup);

        // Simulate an agent dispatch: publish via the dispatcher's navigate path.
        dispatcher.dispatchNavigate(StubLookupContract.class, activeContractLookup);

        assertFalse(token.isCancelled(),
                "agent's own navigate must NOT cancel the loop");
    }

    /** Aftermath suppression: an event firing while the runtime is in its
     *  dispatch+settle window must not cancel — it's a delayed effect of the
     *  agent's own action (URL routing's SET_PRIMARY, etc.) and arrived on
     *  a thread without the dispatcher's thread-local. */
    @Test
    void userEventMonitor_eventDuringDispatchActive_doesNotCancel() {
        AgentRuntime runtime = newRuntime(new ScriptedAgentService(),
                new ActionDispatcher(), new FailingSpawner(),
                allowAuthorization(), new RecordingFeedback(), new TestLookup());

        AtomicBoolean running = (AtomicBoolean) getField(runtime, "running");
        running.set(true);
        AbortToken token = new AbortToken();
        setField(runtime, "currentToken", token);

        TestLookup activeContractLookup = new TestLookup();
        runtime.rebindUserEventMonitor(StubLookupContract.class, activeContractLookup);

        // Simulate "we are inside dispatch + settle" by forcing the flag.
        setField(runtime, "agentDispatchActive", true);

        // Publish without AGENT_DISPATCH set (simulates an async re-render
        // event arriving on the framework's command thread).
        activeContractLookup.publish(EventKeys.SET_PRIMARY, StubLookupContract.class);

        assertFalse(token.isCancelled(),
                "events during agent dispatch+settle must be treated as aftermath");
    }

    /** Aftermath suppression: time-based grace covers late events arriving
     *  within {@code POST_DISPATCH_GRACE_MILLIS} of dispatch end. */
    @Test
    void userEventMonitor_eventWithinGraceWindow_doesNotCancel() {
        AgentRuntime runtime = newRuntime(new ScriptedAgentService(),
                new ActionDispatcher(), new FailingSpawner(),
                allowAuthorization(), new RecordingFeedback(), new TestLookup());

        AtomicBoolean running = (AtomicBoolean) getField(runtime, "running");
        running.set(true);
        AbortToken token = new AbortToken();
        setField(runtime, "currentToken", token);

        TestLookup activeContractLookup = new TestLookup();
        runtime.rebindUserEventMonitor(StubLookupContract.class, activeContractLookup);

        // Simulate "we JUST finished a dispatch."
        setField(runtime, "lastDispatchEndMillis", System.currentTimeMillis());

        activeContractLookup.publish(EventKeys.SET_PRIMARY, StubLookupContract.class);

        assertFalse(token.isCancelled(),
                "events within the post-dispatch grace window must not cancel");
    }

    /** Monitor rebinds when the active contract changes — events on the old
     *  contract no longer trigger interruption. */
    @Test
    void userEventMonitor_rebindsOnSceneChange() {
        AgentRuntime runtime = newRuntime(new ScriptedAgentService(),
                new ActionDispatcher(), new FailingSpawner(),
                allowAuthorization(), new RecordingFeedback(), new TestLookup());

        TestLookup firstContractLookup = new TestLookup();
        runtime.rebindUserEventMonitor(StubLookupContract.class, firstContractLookup);

        TestLookup secondContractLookup = new TestLookup();
        runtime.rebindUserEventMonitor(AlternateStubContract.class, secondContractLookup);

        AtomicBoolean running = (AtomicBoolean) getField(runtime, "running");
        running.set(true);
        AbortToken token = new AbortToken();
        setField(runtime, "currentToken", token);

        // Publish on the OLD lookup — must not cancel (monitor moved).
        firstContractLookup.publish(EventKeys.SET_PRIMARY, StubLookupContract.class);
        assertFalse(token.isCancelled(),
                "publish on stale lookup must not affect the runtime");

        // Publish on the NEW lookup — must cancel.
        secondContractLookup.publish(EventKeys.SET_PRIMARY, StubLookupContract.class);
        assertTrue(token.isCancelled(),
                "publish on the new active contract's lookup must cancel");
    }

    /** Custom InterruptionPolicy is consulted with the origin and key. */
    @Test
    void notifyEvent_invokesCustomPolicy_withOriginAndKey() {
        EventKey.VoidKey key = new EventKey.VoidKey("test.user-action");
        boolean[] called = {false};
        EventOrigin[] capturedOrigin = {null};
        EventKey<?>[] capturedKey = {null};
        InterruptionPolicy policy = (origin, k) -> {
            called[0] = true;
            capturedOrigin[0] = origin;
            capturedKey[0] = k;
            return false;
        };
        AgentRuntime runtime = newRuntime(new ScriptedAgentService(),
                new ActionDispatcher(), new FailingSpawner(),
                allowAuthorization(), new RecordingFeedback(), new TestLookup(),
                policy);

        AtomicBoolean running = (AtomicBoolean) getField(runtime, "running");
        running.set(true);
        setField(runtime, "currentToken", new AbortToken());

        runtime.notifyEvent(EventOrigin.USER, key);

        assertTrue(called[0], "policy must be invoked when running");
        assertEquals(EventOrigin.USER, capturedOrigin[0]);
        assertEquals(key, capturedKey[0]);
    }

    /** Unified plan handling: a {@code PlanResult} enqueues its steps; the
     *  loop iterates through them as ordinary dispatches, each counted by
     *  the budget. When the queue empties the loop returns — no closing
     *  re-prompt (which could cascade if the LLM re-issues the same plan).
     */
    @Test
    void loop_enqueuesPlanSteps_andDispatchesEachThenStops()
            throws InterruptedException {
        EventKey.VoidKey key = new EventKey.VoidKey("test.act");
        ContractAction action = new ContractAction("act", key, "Act");
        ScriptedAgentService service = new ScriptedAgentService(
                new AgentResult.PlanResult(List.of("first step", "second step"), ""),
                new AgentResult.ActionResult(action, ContractActionPayload.EMPTY),
                new AgentResult.ActionResult(action, ContractActionPayload.EMPTY));
        ScriptedDispatcher dispatcher = new ScriptedDispatcher(
                a -> new ActionDispatcher.DispatchResult.Dispatched(
                        a, ContractActionPayload.EMPTY,
                        CompletableFuture.completedFuture(null)));
        RecordingFeedback feedback = new RecordingFeedback(
                m -> m.contains("Step 2/2"));

        AgentRuntime runtime = newRuntime(service, dispatcher, new FailingSpawner(),
                allowAuthorization(), feedback, new TestLookup());

        runtime.submit("do two things");

        assertTrue(feedback.await(ASYNC_TIMEOUT_SECONDS));
        // Brief grace for the runtime to finish post-dispatch bookkeeping
        // before we assert the LLM was not called again.
        Thread.sleep(50);
        assertEquals(2, dispatcher.dispatchCalls.size(),
                "both plan steps must dispatch as ordinary actions");
        assertEquals(3, service.callCount.get(),
                "LLM called: 1 plan classification + 2 per-step calls; no closing re-prompt");
        assertTrue(feedback.messages.stream().anyMatch(m -> m.contains("Plan: 2 steps")),
                "plan size announcement must appear once");
        assertTrue(feedback.messages.stream().anyMatch(m -> m.contains("Step 1/2: first step")),
                "first plan step must be labeled");
        assertTrue(feedback.messages.stream().anyMatch(m -> m.contains("Step 2/2: second step")),
                "second plan step must be labeled");
    }

    /** Bounded follow-up: a scene-changing dispatch in non-queue mode allows
     *  ONE re-prompt so the LLM can react to the new scene (e.g. plan a fill).
     *  Past that, no further non-queue iterations. */
    @Test
    void loop_sceneChangeThenFollowupPlan_dispatchesPlanSteps()
            throws InterruptedException {
        ContractAction openForm = new ContractAction("open_form",
                new EventKey.VoidKey("test.open_form"),
                "Open a form", DispatchEffect.SCENE_CHANGE);
        ContractAction noop = new ContractAction("noop",
                new EventKey.VoidKey("test.noop"), "Noop");
        ScriptedAgentService service = new ScriptedAgentService(
                new AgentResult.ActionResult(openForm, ContractActionPayload.EMPTY),
                new AgentResult.PlanResult(List.of("a", "b"), ""),
                new AgentResult.ActionResult(noop, ContractActionPayload.EMPTY),
                new AgentResult.ActionResult(noop, ContractActionPayload.EMPTY));
        ScriptedDispatcher dispatcher = new ScriptedDispatcher(
                a -> new ActionDispatcher.DispatchResult.Dispatched(
                        a, ContractActionPayload.EMPTY,
                        CompletableFuture.completedFuture(null)));
        RecordingFeedback feedback = new RecordingFeedback(m -> m.contains("Step 2/2"));

        AgentRuntime runtime = newRuntime(service, dispatcher, new FailingSpawner(),
                allowAuthorization(), feedback, new TestLookup());

        runtime.submit("open form and then fill");

        assertTrue(feedback.await(ASYNC_TIMEOUT_SECONDS),
                "follow-up iteration must produce a plan whose steps then dispatch");
        Thread.sleep(50);
        assertEquals(3, dispatcher.dispatchCalls.size(),
                "open_form + 2 plan steps must all dispatch");
        assertEquals(4, service.callCount.get(),
                "LLM called: initial (1) + follow-up plan (2) + 2 step prompts (3, 4)");
    }

    /** Cascade prevention: after a non-queue scene-change, a non-plan
     *  follow-up that's ALSO scene-changing must NOT trigger another
     *  follow-up. The loop stops after the second non-queue iteration. */
    @Test
    void loop_sceneChangeThenSceneChange_stopsAfterTwo()
            throws InterruptedException {
        ContractAction sceneAction = new ContractAction("scene_act",
                new EventKey.VoidKey("test.scene_act"),
                "Scene action", DispatchEffect.SCENE_CHANGE);
        ScriptedAgentService service = new ScriptedAgentService(
                new AgentResult.ActionResult(sceneAction, ContractActionPayload.EMPTY),
                new AgentResult.ActionResult(sceneAction, ContractActionPayload.EMPTY),
                // A 3rd call would indicate the cascade is unbounded.
                new AgentResult.ActionResult(sceneAction, ContractActionPayload.EMPTY));
        ScriptedDispatcher dispatcher = new ScriptedDispatcher(
                a -> new ActionDispatcher.DispatchResult.Dispatched(
                        a, ContractActionPayload.EMPTY,
                        CompletableFuture.completedFuture(null)));
        RecordingFeedback feedback = new RecordingFeedback();

        AgentRuntime runtime = newRuntime(service, dispatcher, new FailingSpawner(),
                allowAuthorization(), feedback, new TestLookup());

        runtime.submit("two scene changes");

        // Wait long enough that any cascading 3rd LLM call would have happened.
        Thread.sleep(500);
        assertEquals(2, service.callCount.get(),
                "loop must bound non-queue iterations at 2 even when each is scene-changing");
    }

    /** Effect gating: a {@code SCENE_CHANGE}-effect action in an intermediate
     *  plan step must block the loop on {@code sceneSettleFuture} until
     *  {@link AgentRuntime#onScene} fires. Without the gate the next step would
     *  see stale context and the LLM would refuse the dependent action (e.g.
     *  "set_field" called before the form is open). */
    @Test
    void loop_sceneChangeAction_blocksUntilOnScene_thenProceeds()
            throws InterruptedException {
        ContractAction sceneChange = new ContractAction("scene_act",
                new EventKey.VoidKey("test.scene_act"),
                "Scene-changing action",
                DispatchEffect.SCENE_CHANGE);
        ContractAction followUp = new ContractAction("follow",
                new EventKey.VoidKey("test.follow"),
                "Follow-up action");

        ScriptedAgentService service = new ScriptedAgentService(
                new AgentResult.PlanResult(List.of("open", "follow"), ""),
                new AgentResult.ActionResult(sceneChange, ContractActionPayload.EMPTY),
                new AgentResult.ActionResult(followUp, ContractActionPayload.EMPTY));
        ScriptedDispatcher dispatcher = new ScriptedDispatcher(
                a -> new ActionDispatcher.DispatchResult.Dispatched(
                        a, ContractActionPayload.EMPTY,
                        CompletableFuture.completedFuture(null)));
        RecordingFeedback feedback = new RecordingFeedback(
                m -> m.equals("Follow-up action"));

        AgentRuntime runtime = newRuntime(service, dispatcher, new FailingSpawner(),
                allowAuthorization(), feedback, new TestLookup());

        // Watchdog: once the runtime arms sceneSettleFuture after the first
        // (SCENE_CHANGE) dispatch, simulate the scene rebuild by calling onScene.
        Thread watcher = new Thread(() -> {
            long deadline = System.currentTimeMillis() + 3_000;
            while (System.currentTimeMillis() < deadline) {
                CompletableFuture<?> f = (CompletableFuture<?>) getField(runtime, "sceneSettleFuture");
                if (f != null && !f.isDone()) {
                    runtime.onScene(null);
                    return;
                }
                try { Thread.sleep(5); } catch (InterruptedException e) { return; }
            }
        }, "settle-watcher");
        watcher.setDaemon(true);
        watcher.start();

        runtime.submit("plan with scene change");

        assertTrue(feedback.await(ASYNC_TIMEOUT_SECONDS),
                "follow-up dispatch must run after onScene completes the gate");
        Thread.sleep(50);
        assertEquals(List.of("scene_act", "follow"), dispatcher.dispatchCalls,
                "scene-change dispatch must precede the follow-up; both must occur");
    }

    /** Post-approval: a queued PlanResult must iterate to completion, not
     *  just enqueue and exit. Reproduces the user-visible "halted after
     *  Plan: N steps" regression. */
    @Test
    void onApprovalDecided_true_runsPlanQueuedResultToCompletion()
            throws InterruptedException {
        EventKey.VoidKey key = new EventKey.VoidKey("test.act");
        ContractAction action = new ContractAction("act", key, "Act");
        // Service is consulted for each enqueued plan step (not for the
        // queued PlanResult itself, which is used as the kickstart).
        ScriptedAgentService service = new ScriptedAgentService(
                new AgentResult.ActionResult(action, ContractActionPayload.EMPTY),
                new AgentResult.ActionResult(action, ContractActionPayload.EMPTY));
        ScriptedDispatcher dispatcher = new ScriptedDispatcher(
                a -> new ActionDispatcher.DispatchResult.Dispatched(
                        a, ContractActionPayload.EMPTY,
                        CompletableFuture.completedFuture(null)));
        ScriptedSpawner spawner = new ScriptedSpawner(
                new SpawnResult.Approved(validSession()));
        RecordingFeedback feedback = new RecordingFeedback(
                m -> m.contains("Step 2/2"));

        AgentRuntime runtime = newRuntime(service, dispatcher, spawner,
                allowAuthorization(), feedback, new TestLookup());

        setField(runtime, "queuedResult",
                new AgentResult.PlanResult(List.of("first", "second"), ""));

        runtime.onApprovalDecided(true);

        assertTrue(feedback.await(ASYNC_TIMEOUT_SECONDS),
                "expected both plan steps to dispatch after approval");
        Thread.sleep(50);
        assertEquals(2, dispatcher.dispatchCalls.size(),
                "queued PlanResult must iterate to completion, not stop after enqueue");
        assertTrue(feedback.messages.stream().anyMatch(m -> m.contains("Plan: 2 steps")));
        assertTrue(feedback.messages.stream().anyMatch(m -> m.contains("Step 1/2: first")));
        assertTrue(feedback.messages.stream().anyMatch(m -> m.contains("Step 2/2: second")));
    }

    /** Loop invariant F: a pre-cancelled token short-circuits the loop —
     *  no LLM call, no dispatch, no feedback. */
    @Test
    void runLoop_withPreCancelledToken_skipsLlmCall() {
        ScriptedAgentService service = new ScriptedAgentService(
                new AgentResult.TextReply("never seen"));
        ScriptedDispatcher dispatcher = new ScriptedDispatcher(
                a -> { throw new AssertionError("dispatch should not run"); });
        RecordingFeedback feedback = new RecordingFeedback();

        AgentRuntime runtime = newRuntime(service, dispatcher, new FailingSpawner(),
                allowAuthorization(), feedback, new TestLookup());

        AbortToken token = new AbortToken();
        token.cancel();
        runtime.runLoop("ignored", null, token, System.currentTimeMillis());

        assertEquals(0, service.callCount.get(),
                "cancelled token must short-circuit before the LLM call");
        assertTrue(feedback.messages.isEmpty(),
                "cancelled loop must emit no feedback (silence preserves caller control)");
    }

    // ==================================================================
    // Test scaffolding
    // ==================================================================

    private static AgentRuntime newRuntime(AgentService service,
                                           ActionDispatcher dispatcher,
                                           AgentSpawner spawner,
                                           Authorization authorization,
                                           AgentFeedback feedback,
                                           Lookup lookup) {
        return newRuntime(service, dispatcher, spawner, authorization, feedback, lookup,
                LoopPolicy.DEFAULT);
    }

    private static AgentRuntime newRuntime(AgentService service,
                                           ActionDispatcher dispatcher,
                                           AgentSpawner spawner,
                                           Authorization authorization,
                                           AgentFeedback feedback,
                                           Lookup lookup,
                                           LoopPolicy policy) {
        return new AgentRuntime(service, dispatcher, spawner,
                authorization, null, lookup, feedback, StubContract.class, policy, "test-runtime");
    }

    private static AgentRuntime newRuntime(AgentService service,
                                           ActionDispatcher dispatcher,
                                           AgentSpawner spawner,
                                           Authorization authorization,
                                           AgentFeedback feedback,
                                           Lookup lookup,
                                           InterruptionPolicy interruptionPolicy) {
        return new AgentRuntime(service, dispatcher, spawner,
                authorization, null, lookup, feedback, StubContract.class, LoopPolicy.DEFAULT,
                interruptionPolicy, "test-runtime");
    }

    private static Authorization allowAuthorization() {
        return new Authorization((AccessPolicy) attrs -> new AccessDecision.Allow(),
                Attributes.empty());
    }

    private static Authorization denyAuthorization(String reason) {
        return new Authorization((AccessPolicy) attrs -> new AccessDecision.Deny(reason),
                Attributes.empty());
    }

    private static AgentSession validSession() {
        DelegationGrant grant = new DelegationGrant(
                "grant-id", Attributes.empty(), Instant.now(), null);
        return new AgentSession("session-id", grant);
    }

    private static Object pendingAction(ContractAction action) {
        // PendingAction is a private record inside AgentRuntime — instantiate via reflection.
        try {
            Class<?> cls = Class.forName("rsp.compositions.agent.AgentRuntime$PendingAction");
            java.lang.reflect.Constructor<?> ctor = cls.getDeclaredConstructors()[0];
            ctor.setAccessible(true);
            return ctor.newInstance(action, ContractActionPayload.EMPTY);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static void setField(Object target, String name, Object value) {
        try {
            Field f = target.getClass().getDeclaredField(name);
            f.setAccessible(true);
            f.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static Object getField(Object target, String name) {
        try {
            Field f = target.getClass().getDeclaredField(name);
            f.setAccessible(true);
            return f.get(target);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    /** Captures messages and counts down a latch when a trigger predicate matches. */
    private static final class RecordingFeedback implements AgentFeedback {
        final List<String> messages = new CopyOnWriteArrayList<>();
        final List<String> updates = new CopyOnWriteArrayList<>();
        private final CountDownLatch latch;
        private final java.util.function.Predicate<String> trigger;

        RecordingFeedback() {
            this(m -> false);
        }

        RecordingFeedback(java.util.function.Predicate<String> trigger) {
            this.latch = new CountDownLatch(1);
            this.trigger = trigger;
        }

        @Override
        public void send(String message) {
            messages.add(message);
            if (trigger.test(message)) {
                latch.countDown();
            }
        }

        @Override
        public void updateLast(String message) {
            updates.add(message);
        }

        boolean await(long seconds) throws InterruptedException {
            return latch.await(seconds, TimeUnit.SECONDS);
        }
    }

    /** Returns a queued sequence of {@link AgentResult}s, one per call. */
    private static final class ScriptedAgentService extends AgentService {
        final AtomicInteger callCount = new AtomicInteger(0);
        private final Deque<AgentResult> results;

        ScriptedAgentService(AgentResult... results) {
            this.results = new ArrayDeque<>(Arrays.asList(results));
        }

        @Override
        public AgentResult handlePrompt(String prompt, ContractProfile profile,
                                        rsp.compositions.composition.StructureNode tree) {
            callCount.incrementAndGet();
            AgentResult next = results.poll();
            return next != null ? next : new AgentResult.TextReply("(exhausted)");
        }

        @Override
        public AgentResult handlePrompt(String prompt, ContractProfile profile,
                                        rsp.compositions.composition.StructureNode tree,
                                        Consumer<String> partial) {
            return handlePrompt(prompt, profile, tree);
        }
    }

    private static final class ScriptedSpawner implements AgentSpawner {
        final AtomicInteger callCount = new AtomicInteger(0);
        private final SpawnResult result;

        ScriptedSpawner(SpawnResult result) {
            this.result = result;
        }

        @Override
        public SpawnResult spawn(SpawnRequest request, Lookup lookup) {
            callCount.incrementAndGet();
            return result;
        }
    }

    private static final class FailingSpawner implements AgentSpawner {
        @Override
        public SpawnResult spawn(SpawnRequest request, Lookup lookup) {
            throw new AssertionError("spawn() should not be called in this test");
        }
    }

    /** Minimal concrete {@link ViewContract} used as a navigation target in
     *  tests that need a {@link AgentResult.NavigateResult} carrying a real class. */
    private static final class StubContract extends ViewContract {
        StubContract(Lookup lookup) { super(lookup); }
        @Override public rsp.component.ComponentContext enrichContext(rsp.component.ComponentContext ctx) { return ctx; }
        @Override public String title() { return "Stub"; }
    }

    /** Concrete contract whose lookup is the one supplied at construction —
     *  used so {@link ActionDispatcher#dispatch} (which publishes on
     *  {@code contract.lookup()}) targets a test-controlled lookup. */
    private static final class StubLookupContract extends ViewContract {
        StubLookupContract(Lookup lookup) { super(lookup); }
        @Override public rsp.component.ComponentContext enrichContext(rsp.component.ComponentContext ctx) { return ctx; }
        @Override public String title() { return "StubLookup"; }
    }

    /** Second stub used to simulate a scene change in monitor-rebind tests. */
    private static final class AlternateStubContract extends ViewContract {
        AlternateStubContract(Lookup lookup) { super(lookup); }
        @Override public rsp.component.ComponentContext enrichContext(rsp.component.ComponentContext ctx) { return ctx; }
        @Override public String title() { return "Alternate"; }
    }

    /** Dispatcher that produces a caller-supplied DispatchResult per call,
     *  recording the action name. Used for tests that need to drive the loop
     *  through specific dispatch outcomes without wiring a real contract. */
    private static final class ScriptedDispatcher extends ActionDispatcher {
        final List<String> dispatchCalls = new CopyOnWriteArrayList<>();
        private final Function<ContractAction, DispatchResult> behavior;

        ScriptedDispatcher(Function<ContractAction, DispatchResult> behavior) {
            this.behavior = behavior;
        }

        @Override
        public DispatchResult dispatch(ContractAction action, ContractActionPayload payload,
                                       ViewContract contract, Lookup lookup, ActionGate gate) {
            dispatchCalls.add(action.action());
            return behavior.apply(action);
        }
    }

    /** Records calls to {@link #dispatchDirect} so tests can verify routing
     *  without needing a fully-wired routed contract / Scene. */
    private static final class RecordingDispatcher extends ActionDispatcher {
        final List<String> directCalls = new CopyOnWriteArrayList<>();

        @Override
        public DispatchResult dispatchDirect(ContractAction action,
                                             ContractActionPayload payload,
                                             ViewContract contract) {
            directCalls.add(action.action());
            return new DispatchResult.Dispatched(action, payload,
                    CompletableFuture.completedFuture(null));
        }
    }
}
