package rsp.compositions.agent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import rsp.component.EventKey;
import rsp.component.Lookup;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class IntentDispatcherTests {

    private IntentDispatcher dispatcher;
    private IntentGate allowAllGate;

    @BeforeEach
    void setUp() {
        dispatcher = new IntentDispatcher();
        allowAllGate = (intent, lookup) -> new GateResult.Allow(intent);
    }

    @Test
    void dispatches_void_action() {
        EventKey.VoidKey key = new EventKey.VoidKey("test.void");
        List<String> published = new ArrayList<>();

        Lookup contractLookup = new StubLookup() {
            @Override
            public void publish(EventKey.VoidKey k) {
                published.add(k.name());
            }
        };

        StubContract contract = new StubContract(
            List.of(new AgentAction("do_thing", key, "Do a thing", null)), contractLookup);

        IntentDispatcher.DispatchResult result = dispatcher.dispatch(
            new AgentIntent("do_thing"), contract, new StubLookup(), allowAllGate);

        assertInstanceOf(IntentDispatcher.DispatchResult.Dispatched.class, result);
        assertEquals(List.of("test.void"), published);
    }

    @Test
    void dispatches_simple_key_action_with_payload() {
        EventKey.SimpleKey<String> key = new EventKey.SimpleKey<>("test.simple", String.class);
        List<Object> published = new ArrayList<>();

        Lookup contractLookup = new StubLookup() {
            @Override
            @SuppressWarnings("unchecked")
            public <T> void publish(EventKey<T> k, T payload) {
                published.add(payload);
            }
        };

        StubContract contract = new StubContract(
            List.of(new AgentAction("edit", key, "Edit item", "String: id")), contractLookup);

        dispatcher.dispatch(
            new AgentIntent("edit", Map.of("payload", "42")),
            contract, new StubLookup(), allowAllGate);

        assertEquals(List.of("42"), published);
    }

    @Test
    void returns_unknown_action_for_undeclared_action() {
        StubContract contract = new StubContract(List.of());

        IntentDispatcher.DispatchResult result = dispatcher.dispatch(
            new AgentIntent("nonexistent"), contract, new StubLookup(), allowAllGate);

        assertInstanceOf(IntentDispatcher.DispatchResult.UnknownAction.class, result);
        assertEquals("nonexistent",
            ((IntentDispatcher.DispatchResult.UnknownAction) result).action());
    }

    @Test
    void returns_blocked_when_gate_blocks() {
        IntentGate blockGate = (intent, lookup) ->
            new GateResult.Block("Not permitted");

        StubContract contract = new StubContract(List.of());

        IntentDispatcher.DispatchResult result = dispatcher.dispatch(
            new AgentIntent("delete"), contract, new StubLookup(), blockGate);

        assertInstanceOf(IntentDispatcher.DispatchResult.Blocked.class, result);
        assertEquals("Not permitted",
            ((IntentDispatcher.DispatchResult.Blocked) result).reason());
    }

    @Test
    void returns_awaiting_confirmation_when_gate_confirms() {
        AgentIntent intent = new AgentIntent("delete");
        IntentGate confirmGate = (i, lookup) ->
            new GateResult.Confirm("Are you sure?", i);

        StubContract contract = new StubContract(List.of());

        IntentDispatcher.DispatchResult result = dispatcher.dispatch(
            intent, contract, new StubLookup(), confirmGate);

        assertInstanceOf(IntentDispatcher.DispatchResult.AwaitingConfirmation.class, result);
        assertEquals("Are you sure?",
            ((IntentDispatcher.DispatchResult.AwaitingConfirmation) result).question());
    }

    @Test
    void dispatch_direct_bypasses_gate() {
        EventKey.VoidKey key = new EventKey.VoidKey("test.direct");
        List<String> published = new ArrayList<>();

        Lookup contractLookup = new StubLookup() {
            @Override
            public void publish(EventKey.VoidKey k) {
                published.add(k.name());
            }
        };

        StubContract contract = new StubContract(
            List.of(new AgentAction("act", key, "An action", null)), contractLookup);

        dispatcher.dispatchDirect(new AgentIntent("act"), contract, new StubLookup());

        assertEquals(List.of("test.direct"), published);
    }

    // --- Stubs ---

    /** Minimal ViewContract stub for testing. */
    private static class StubContract extends rsp.compositions.contract.ViewContract {
        private final List<AgentAction> actions;

        StubContract(List<AgentAction> actions) {
            this(actions, new StubLookup());
        }

        StubContract(List<AgentAction> actions, Lookup lookup) {
            super(lookup);
            this.actions = actions;
        }

        @Override
        public List<AgentAction> agentActions() {
            return actions;
        }

        @Override
        public rsp.component.ComponentContext enrichContext(rsp.component.ComponentContext ctx) {
            return ctx;
        }

        @Override
        public String title() {
            return "Stub";
        }
    }

    /** Minimal Lookup stub — overridden per test for publish tracking. */
    private static class StubLookup implements Lookup {
        @Override public <T> T get(rsp.component.ContextKey<T> key) { return null; }
        @Override public <T> T get(Class<T> key) { return null; }
        @Override public <T> T getRequired(rsp.component.ContextKey<T> key) { throw new IllegalStateException(); }
        @Override public <T> T getRequired(Class<T> key) { throw new IllegalStateException(); }
        @Override public <T> Lookup with(rsp.component.ContextKey<T> key, T value) { return this; }
        @Override public <T> Lookup with(Class<T> clazz, T instance) { return this; }
        @Override public <T> void publish(EventKey<T> key, T payload) {}
        @Override public void publish(EventKey.VoidKey key) {}
        @Override public <T> Registration subscribe(EventKey<T> key, java.util.function.BiConsumer<String, T> handler) {
            return () -> {};
        }
        @Override public Registration subscribe(EventKey.VoidKey key, Runnable handler) {
            return () -> {};
        }
    }
}
