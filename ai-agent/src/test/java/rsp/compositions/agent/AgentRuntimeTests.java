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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

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

    // ==================================================================
    // Test scaffolding
    // ==================================================================

    private static AgentRuntime newRuntime(AgentService service,
                                           ActionDispatcher dispatcher,
                                           AgentSpawner spawner,
                                           Authorization authorization,
                                           AgentFeedback feedback,
                                           Lookup lookup) {
        return new AgentRuntime(service, dispatcher, spawner,
                authorization, null, lookup, feedback, "test-runtime");
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
