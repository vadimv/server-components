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
        assertEquals(DelegationApprovalContract.class, payload.contractClass());
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

    /** Loop invariant E: while a loop is running, new submits are rejected. */
    @Test
    void submit_whileLoopRunning_isRejected() {
        RecordingFeedback feedback = new RecordingFeedback();
        ScriptedAgentService service = new ScriptedAgentService();
        AgentRuntime runtime = newRuntime(service, new ActionDispatcher(),
                new FailingSpawner(), allowAuthorization(), feedback, new TestLookup());

        AtomicBoolean running = (AtomicBoolean) getField(runtime, "running");
        running.set(true);

        runtime.submit("anything");

        assertEquals(List.of("Still processing previous prompt..."), feedback.messages);
        assertEquals(0, service.callCount.get(),
                "LLM must not be invoked when a loop is in progress");
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
                authorization, null, lookup, feedback, policy, "test-runtime");
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
